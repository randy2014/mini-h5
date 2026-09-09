package com.mini.novel.media.support;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.color.ColorSpace;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

/**
 * 图片处理：预算式 JPEG 压缩 + 缩略图（JDK ImageIO 实现，无外部图像库）。
 *
 * 设计目标（见 docs/media-pool-design.md §5.1/5.3）：
 * - 正文成品：预算 targetBudget(默认 1MB)，quality 85 起二分微调，长边 > maxSide(2400) 等比降；
 *   质量下限 75 仍超预算时按 0.9 逐级降长边（下限 minSide 1600）；最终 ≤ budget*1.5 内接受并记 oversize。
 * - 缩略图：长边 thumbSide(400)，质量 0.8，体积约 20-50KB。
 * - 透明 PNG 合成白底（JPEG 无 alpha）；EXIF orientation(3/6/8) 物理转正。
 */
public final class ImageCompressor {

    private static final float QUALITY_START = 0.85f;
    private static final float QUALITY_HIGH = 0.92f;
    private static final float QUALITY_LOW = 0.75f;
    private static final int BISECT_MAX = 5;

    private final long targetBudget;
    private final int maxSide;
    private final int minSide;
    private final int thumbSide;

    public ImageCompressor() {
        this(1024L * 1024L, 2400, 1600, 400);
    }

    public ImageCompressor(long targetBudget, int maxSide, int minSide, int thumbSide) {
        this.targetBudget = targetBudget;
        this.maxSide = maxSide;
        this.minSide = minSide;
        this.thumbSide = thumbSide;
    }

    /** 成品压缩结果。 */
    public static final class Result {
        private final byte[] jpeg;
        private final int width;
        private final int height;
        private final boolean oversize;

        Result(byte[] jpeg, int width, int height, boolean oversize) {
            this.jpeg = jpeg;
            this.width = width;
            this.height = height;
            this.oversize = oversize;
        }

        public byte[] getJpeg() { return jpeg; }
        public int getWidth() { return width; }
        public int getHeight() { return height; }
        public boolean isOversize() { return oversize; }
    }

    /**
     * 解码并预算式压缩为 JPEG 成品。
     *
     * @throws IOException 无法解码或编码失败
     */
    public Result compress(byte[] source) throws IOException {
        BufferedImage base = decode(source);
        // 等比限制长边，防止超清大图先吃掉内存与时间
        int[] fit = fitDimension(base.getWidth(), base.getHeight(), maxSide);
        BufferedImage scaled = resizeIfNeeded(base, fit[0], fit[1]);

        boolean oversize = false;
        byte[] best = null;
        // 1) quality 二分找预算内最高质量
        float lo = QUALITY_LOW;
        float hi = QUALITY_HIGH;
        float q = QUALITY_START;
        for (int i = 0; i < BISECT_MAX; i++) {
            byte[] bytes = encodeJpeg(scaled, q);
            if (bytes.length <= targetBudget) {
                best = bytes;
                lo = q;
                q = (q + QUALITY_HIGH) / 2f;
            } else {
                hi = q;
                q = (lo + q) / 2f;
            }
        }
        if (best == null) {
            best = encodeJpeg(scaled, QUALITY_LOW);
        }
        // 2) 质量下限仍超预算 → 逐级降长边
        int w = scaled.getWidth();
        int h = scaled.getHeight();
        while (best.length > targetBudget && w > minSide) {
            int nw = Math.max(minSide, (int) (w * 0.9));
            int nh = Math.max(minSide, (int) (h * 0.9));
            scaled = resizeIfNeeded(scaled, nw, nh);
            best = encodeJpeg(scaled, QUALITY_LOW);
            w = nw;
            h = nh;
        }
        if (best.length > (long) (targetBudget * 1.5)) {
            oversize = true; // 极端图仍超出 1.5 倍预算，接受并标记
        }
        return new Result(best, scaled.getWidth(), scaled.getHeight(), oversize);
    }

    /** 缩略图（长边 thumbSide，质量 0.8）。 */
    public Result thumbnail(byte[] source) throws IOException {
        BufferedImage oriented = decode(source);
        int[] fit = fitDimension(oriented.getWidth(), oriented.getHeight(), thumbSide);
        BufferedImage scaled = resizeIfNeeded(oriented, fit[0], fit[1]);
        return new Result(encodeJpeg(scaled, 0.8f), scaled.getWidth(), scaled.getHeight(), false);
    }

    // ---------- 内部 ----------

    private static BufferedImage decode(byte[] source) throws IOException {
        BufferedImage img;
        try (InputStream in = new ByteArrayInputStream(source)) {
            img = ImageIO.read(in);
        }
        if (img == null) {
            throw new IOException("无法解码图片（不支持的类型或文件损坏）");
        }
        BufferedImage oriented = applyExifOrientation(source, img);
        if (oriented.getType() == BufferedImage.TYPE_INT_ARGB) {
            oriented = flattenToWhite(oriented);
        }
        return oriented;
    }

    private static int[] fitDimension(int w, int h, int maxSide) {
        int longer = Math.max(w, h);
        if (w <= 0 || h <= 0 || longer <= maxSide) {
            return new int[]{w, h};
        }
        double scale = (double) maxSide / longer;
        return new int[]{(int) Math.round(w * scale), (int) Math.round(h * scale)};
    }

    private static BufferedImage resizeIfNeeded(BufferedImage src, int width, int height) {
        if (width == src.getWidth() && height == src.getHeight()) {
            return src;
        }
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);
        g.drawImage(src, 0, 0, width, height, null);
        g.dispose();
        return out;
    }

    private static byte[] encodeJpeg(BufferedImage img, float quality) throws IOException {
        // 统一重绘为无 profile 的 TYPE_INT_RGB，规避源图 colorspace/alpha 导致的
        // JPEGImageWriter "Bogus input colorspace"（透明 PNG / 带 ICC 图常见）
        BufferedImage rgb;
        if (img.getType() == BufferedImage.TYPE_INT_RGB && img.getColorModel().getColorSpace().getType() == ColorSpace.TYPE_RGB) {
            rgb = img;
        } else {
            rgb = flattenToWhite(img);
        }
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            throw new IOException("无 JPEG 编码器");
        }
        ImageWriter writer = writers.next();
        try {
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(Math.max(0.05f, Math.min(0.98f, quality)));
            try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
                 ImageOutputStream ios = ImageIO.createImageOutputStream(bos)) {
                writer.setOutput(ios);
                writer.write(null, new IIOImage(rgb, null, null), param);
                ios.flush();
                return bos.toByteArray();
            }
        } finally {
            writer.dispose();
        }
    }

    private static BufferedImage flattenToWhite(BufferedImage src) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, out.getWidth(), out.getHeight());
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return out;
    }

    /** EXIF orientation：仅处理最常见的 3(180°)/6(顺时针90°)/8(逆时针90°)，其余按原样。 */
    private static BufferedImage applyExifOrientation(byte[] source, BufferedImage img) {
        int orientation = readExifOrientation(source);
        if (orientation <= 1) {
            return img;
        }
        int w = img.getWidth();
        int h = img.getHeight();
        switch (orientation) {
            case 3: {
                BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = out.createGraphics();
                g.rotate(Math.toRadians(180), w / 2.0, h / 2.0);
                g.drawImage(img, 0, 0, null);
                g.dispose();
                return out;
            }
            case 6: {
                BufferedImage out = new BufferedImage(h, w, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = out.createGraphics();
                g.rotate(Math.toRadians(90));
                g.translate(0, -h);
                g.drawImage(img, 0, 0, null);
                g.dispose();
                return out;
            }
            case 8: {
                BufferedImage out = new BufferedImage(h, w, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = out.createGraphics();
                g.rotate(Math.toRadians(-90));
                g.translate(-w, 0);
                g.drawImage(img, 0, 0, null);
                g.dispose();
                return out;
            }
            default:
                return img; // 2/4/5/7 翻转类少见，本期不处理
        }
    }

    /** 解析 JPEG APP1 EXIF Orientation(0x0112)。非 JPEG / 无标记返回 1。 */
    private static int readExifOrientation(byte[] src) {
        try {
            if (src == null || src.length < 16 || (src[0] & 0xFF) != 0xFF || (src[1] & 0xFF) != 0xD8) {
                return 1;
            }
            int i = 2;
            while (i + 4 < src.length) {
                if ((src[i] & 0xFF) != 0xFF) {
                    return 1;
                }
                int marker = src[i + 1] & 0xFF;
                if (marker == 0xDA || marker == 0xD9) {
                    return 1; // SOS/EOI
                }
                int len = ((src[i + 2] & 0xFF) << 8) | (src[i + 3] & 0xFF);
                if (len < 2 || i + 2 + len > src.length) {
                    return 1;
                }
                if (marker == 0xE1 && len >= 14
                        && src[i + 4] == 'E' && src[i + 5] == 'x' && src[i + 6] == 'i' && src[i + 7] == 'f'
                        && src[i + 8] == 0 && src[i + 9] == 0) {
                    return readOrientationFromTiff(src, i + 10);
                }
                i += 2 + len;
            }
        } catch (Exception ignored) {
        }
        return 1;
    }

    private static int readOrientationFromTiff(byte[] src, int tiff) {
        try {
            if (tiff + 8 > src.length) {
                return 1;
            }
            boolean little = src[tiff] == 'I';
            if (src[tiff] != 'I' && src[tiff] != 'M') {
                return 1;
            }
            int ifd0 = readInt(src, tiff + 4, little);
            if (ifd0 < 0 || tiff + ifd0 + 2 > src.length) {
                return 1;
            }
            int count = readInt(src, tiff + ifd0, little);
            for (int e = 0; e < count && e < 64; e++) {
                int p = tiff + ifd0 + 2 + e * 12;
                if (p + 12 > src.length) {
                    break;
                }
                if (readInt(src, p, little) == 0x0112) {
                    int v = readInt(src, p + 8, little);
                    return v >= 1 && v <= 8 ? v : 1;
                }
            }
        } catch (Exception ignored) {
        }
        return 1;
    }

    private static int readInt(byte[] src, int pos, boolean little) {
        if (pos < 0 || pos + 4 > src.length) {
            return 0;
        }
        if (little) {
            return (src[pos] & 0xFF) | ((src[pos + 1] & 0xFF) << 8)
                    | ((src[pos + 2] & 0xFF) << 16) | ((src[pos + 3] & 0xFF) << 24);
        }
        return ((src[pos] & 0xFF) << 24) | ((src[pos + 1] & 0xFF) << 16)
                | ((src[pos + 2] & 0xFF) << 8) | (src[pos + 3] & 0xFF);
    }
}
