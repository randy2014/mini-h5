package com.mini.novel.media.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class ImageCompressorTest {

    /** 生成一张带噪点/渐变的大图用于压缩测试。 */
    private byte[] bigPhoto(int w, int h) throws IOException {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        for (int y = 0; y < h; y += 8) {
            for (int x = 0; x < w; x += 8) {
                g.setColor(new Color((x * 31) % 256, (y * 47) % 256, (x + y) % 256));
                g.fillRect(x, y, 8, 8);
            }
        }
        g.setColor(Color.BLACK);
        g.drawString("test " + w + "x" + h, 40, 80);
        g.dispose();
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            ImageIO.write(img, "png", bos);
            return bos.toByteArray();
        }
    }

    @Test
    void compressLargeJpegStaysWithinBudget() throws IOException {
        ImageCompressor c = new ImageCompressor(); // budget 1MB, maxSide 2400
        byte[] src = bigPhoto(3200, 2400);
        ImageCompressor.Result r = c.compress(src);
        assertTrue(r.getWidth() <= 2400, "长边应降至 2400 内, got " + r.getWidth());
        assertTrue(r.getHeight() <= 2400);
        assertTrue(r.getJpeg().length <= 1024L * 1024L * 1.5,
                "成品应 ≤1.5MB(预算1MB+容差), got " + r.getJpeg().length);
        // JPEG 魔数
        assertEquals(0xFF, r.getJpeg()[0] & 0xFF);
        assertEquals(0xD8, r.getJpeg()[1] & 0xFF);
    }

    @Test
    void smallImageKeepsDimensionAndStaysTiny() throws IOException {
        ImageCompressor c = new ImageCompressor();
        byte[] src = bigPhoto(600, 400);
        ImageCompressor.Result r = c.compress(src);
        assertEquals(600, r.getWidth());
        assertEquals(400, r.getHeight());
        assertTrue(r.getJpeg().length <= 1024L * 1024L, "小图应远小于预算");
    }

    @Test
    void thumbnailIsSmall() throws IOException {
        ImageCompressor c = new ImageCompressor();
        byte[] src = bigPhoto(3200, 2400);
        ImageCompressor.Result t = c.thumbnail(src);
        assertTrue(Math.max(t.getWidth(), t.getHeight()) <= 400, "缩略长边 ≤400");
        assertTrue(t.getJpeg().length <= 100 * 1024, "缩略应 ≤100KB");
    }

    @Test
    void transparentPngFlattensWithoutError() throws IOException {
        BufferedImage img = new BufferedImage(1200, 900, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(10, 120, 100, 160));
        g.fillOval(100, 100, 800, 600);
        g.dispose();
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            ImageIO.write(img, "png", bos);
            ImageCompressor c = new ImageCompressor();
            ImageCompressor.Result r = c.compress(bos.toByteArray());
            assertTrue(r.getJpeg().length > 0);
            assertTrue(r.getWidth() == 1200 && r.getHeight() == 900);
        }
    }

    @Test
    void invalidInputThrows() {
        ImageCompressor c = new ImageCompressor();
        try {
            c.compress(new byte[]{1, 2, 3, 4, 5, 6});
            org.junit.jupiter.api.Assertions.fail("应抛出 IOException");
        } catch (IOException expected) {
            // ok
        }
    }
}
