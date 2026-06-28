package com.mini.novel.crawler.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mini.novel.book.entity.Chapter;
import com.mini.novel.book.entity.ChapterSourceMapping;
import com.mini.novel.book.entity.Novel;
import com.mini.novel.book.entity.NovelIdentity;
import com.mini.novel.book.entity.NovelSourceMapping;
import com.mini.novel.book.mapper.ChapterMapper;
import com.mini.novel.book.mapper.ChapterSourceMappingMapper;
import com.mini.novel.book.mapper.NovelIdentityMapper;
import com.mini.novel.book.mapper.NovelMapper;
import com.mini.novel.book.mapper.NovelSourceMappingMapper;
import com.mini.novel.crawler.entity.CrawlBookRaw;
import com.mini.novel.crawler.entity.CrawlChapterRaw;
import com.mini.novel.crawler.entity.CrawlContentRaw;
import com.mini.novel.crawler.entity.CrawlMergeItem;
import com.mini.novel.crawler.entity.CrawlMergeTask;
import com.mini.novel.crawler.mapper.CrawlBookRawMapper;
import com.mini.novel.crawler.mapper.CrawlChapterRawMapper;
import com.mini.novel.crawler.mapper.CrawlContentRawMapper;
import com.mini.novel.crawler.mapper.CrawlMergeItemMapper;
import com.mini.novel.crawler.mapper.CrawlMergeTaskMapper;
import com.mini.novel.crawler.service.CrawlerMergeService;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class CrawlerMergeServiceImpl implements CrawlerMergeService {
    private static final int MIN_CONTENT_LENGTH = 120;

    private final CrawlMergeTaskMapper mergeTaskMapper;
    private final CrawlMergeItemMapper mergeItemMapper;
    private final CrawlBookRawMapper bookRawMapper;
    private final CrawlChapterRawMapper chapterRawMapper;
    private final CrawlContentRawMapper contentRawMapper;
    private final NovelIdentityMapper identityMapper;
    private final NovelSourceMappingMapper novelSourceMappingMapper;
    private final ChapterSourceMappingMapper chapterSourceMappingMapper;
    private final NovelMapper novelMapper;
    private final ChapterMapper chapterMapper;

    public CrawlerMergeServiceImpl(CrawlMergeTaskMapper mergeTaskMapper,
                                   CrawlMergeItemMapper mergeItemMapper,
                                   CrawlBookRawMapper bookRawMapper,
                                   CrawlChapterRawMapper chapterRawMapper,
                                   CrawlContentRawMapper contentRawMapper,
                                   NovelIdentityMapper identityMapper,
                                   NovelSourceMappingMapper novelSourceMappingMapper,
                                   ChapterSourceMappingMapper chapterSourceMappingMapper,
                                   NovelMapper novelMapper,
                                   ChapterMapper chapterMapper) {
        this.mergeTaskMapper = mergeTaskMapper;
        this.mergeItemMapper = mergeItemMapper;
        this.bookRawMapper = bookRawMapper;
        this.chapterRawMapper = chapterRawMapper;
        this.contentRawMapper = contentRawMapper;
        this.identityMapper = identityMapper;
        this.novelSourceMappingMapper = novelSourceMappingMapper;
        this.chapterSourceMappingMapper = chapterSourceMappingMapper;
        this.novelMapper = novelMapper;
        this.chapterMapper = chapterMapper;
    }

    @Override
    public void mergePending() {
        List<CrawlMergeTask> tasks = mergeTaskMapper.selectList(new QueryWrapper<CrawlMergeTask>()
                .eq("status", "PENDING")
                .orderByAsc("id")
                .last("LIMIT 10"));
        for (CrawlMergeTask task : tasks) {
            mergeTask(task);
        }
    }

    @Override
    public void mergeByCrawlTaskId(Long crawlTaskId) {
        CrawlMergeTask task = mergeTaskMapper.selectOne(new QueryWrapper<CrawlMergeTask>()
                .eq("crawl_task_id", crawlTaskId)
                .last("LIMIT 1"));
        if (task != null) {
            mergeTask(task);
        }
    }

    @Transactional
    public void mergeTask(CrawlMergeTask task) {
        if (task == null || (!"PENDING".equals(task.status) && !"FAILED".equals(task.status))) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        task.status = "MERGING";
        task.startedAt = task.startedAt == null ? now : task.startedAt;
        task.updatedAt = now;
        task.message = "清洗入库执行中：正在进行小说去重、正文质量校验和章节写入。";
        mergeTaskMapper.updateById(task);

        int total = 0;
        int merged = 0;
        int pending = 0;
        int failed = 0;
        try {
            List<CrawlBookRaw> books = bookRawMapper.selectList(new QueryWrapper<CrawlBookRaw>()
                    .orderByDesc("crawled_at")
                    .last("LIMIT 300"));
            for (CrawlBookRaw book : books) {
                List<CrawlChapterRaw> chapters = chapterRawMapper.selectList(new QueryWrapper<CrawlChapterRaw>()
                        .eq("book_raw_id", book.id)
                        .orderByAsc("chapter_no")
                        .last("LIMIT 500"));
                if (chapters.isEmpty()) {
                    continue;
                }
                total++;
                MergeOutcome outcome = mergeBook(task, book, chapters);
                if (outcome == MergeOutcome.MERGED) {
                    merged++;
                } else if (outcome == MergeOutcome.PENDING_REVIEW) {
                    pending++;
                } else {
                    failed++;
                }
            }
            task.status = failed == 0 ? "MERGED" : "PARTIAL_MERGED";
            task.message = "清洗入库完成：处理 " + total + " 本，入库 " + merged + " 本，待审核 " + pending + " 本，失败 " + failed + " 本。";
        } catch (Exception ex) {
            task.status = "FAILED";
            task.message = "清洗入库失败：" + ex.getMessage();
        } finally {
            task.totalCount = total;
            task.mergedCount = merged;
            task.pendingReviewCount = pending;
            task.failedCount = failed;
            task.finishedAt = LocalDateTime.now();
            task.updatedAt = task.finishedAt;
            mergeTaskMapper.updateById(task);
        }
    }

    private MergeOutcome mergeBook(CrawlMergeTask task, CrawlBookRaw book, List<CrawlChapterRaw> chapters) {
        LocalDateTime now = LocalDateTime.now();
        NovelIdentity identity = upsertIdentity(book, now);
        NovelSourceMapping novelMapping = upsertNovelMapping(identity, book, now);
        Novel novel = ensureNovel(identity, novelMapping, book, now);

        int mergedChapters = 0;
        int pendingChapters = 0;
        int failedChapters = 0;
        for (CrawlChapterRaw rawChapter : chapters) {
            CrawlContentRaw content = contentRawMapper.selectOne(new QueryWrapper<CrawlContentRaw>()
                    .eq("chapter_raw_id", rawChapter.id)
                    .last("LIMIT 1"));
            ContentQuality quality = evaluateContent(content == null ? "" : content.content);
            if (!quality.accepted) {
                upsertChapterMapping(novelMapping, rawChapter, null, "PENDING_REVIEW", now);
                pendingChapters++;
                continue;
            }
            try {
                Chapter chapter = upsertChapter(novel, rawChapter, content.content, now);
                upsertChapterMapping(novelMapping, rawChapter, chapter.getId(), "MERGED", now);
                mergedChapters++;
            } catch (Exception ex) {
                upsertChapterMapping(novelMapping, rawChapter, null, "FAILED", now);
                failedChapters++;
            }
        }

        refreshNovelLatestChapter(novel);
        String status;
        String message;
        MergeOutcome outcome;
        if (mergedChapters > 0) {
            status = pendingChapters == 0 && failedChapters == 0 ? "MERGED" : "PARTIAL_MERGED";
            message = "正文入库 " + mergedChapters + " 章，待审核 " + pendingChapters + " 章，失败 " + failedChapters + " 章。";
            novelMapping.contentStatus = "CONTENT_READY";
            outcome = MergeOutcome.MERGED;
        } else if (pendingChapters > 0) {
            status = "PENDING_REVIEW";
            message = "未发现达标正文，已保留映射关系，等待补正文或人工审核。";
            novelMapping.contentStatus = "PENDING_REVIEW";
            outcome = MergeOutcome.PENDING_REVIEW;
        } else {
            status = "FAILED";
            message = "章节清洗失败。";
            novelMapping.contentStatus = "FAILED";
            outcome = MergeOutcome.FAILED;
        }
        novelMapping.matchStatus = status;
        novelMapping.updatedAt = LocalDateTime.now();
        novelSourceMappingMapper.updateById(novelMapping);
        upsertMergeItem(task, book, identity, novel, status, message);
        return outcome;
    }

    private NovelIdentity upsertIdentity(CrawlBookRaw book, LocalDateTime now) {
        String normalizedTitle = normalizeIdentity(book.title);
        String normalizedAuthor = normalizeIdentity(book.author);
        NovelIdentity identity = identityMapper.selectOne(new QueryWrapper<NovelIdentity>()
                .eq("normalized_title", normalizedTitle)
                .eq("normalized_author", normalizedAuthor)
                .last("LIMIT 1"));
        if (identity == null) {
            identity = new NovelIdentity();
            identity.canonicalTitle = limit(book.title, 128);
            identity.canonicalAuthor = limit(StringUtils.hasText(book.author) ? book.author : "未知作者", 64);
            identity.normalizedTitle = normalizedTitle;
            identity.normalizedAuthor = normalizedAuthor;
            identity.matchStatus = "ACTIVE";
            identity.confidenceScore = 100;
            identity.createdAt = now;
            identity.updatedAt = now;
            identityMapper.insert(identity);
        }
        return identity;
    }

    private NovelSourceMapping upsertNovelMapping(NovelIdentity identity, CrawlBookRaw book, LocalDateTime now) {
        NovelSourceMapping mapping = novelSourceMappingMapper.selectOne(new QueryWrapper<NovelSourceMapping>()
                .eq("source_code", book.sourceCode)
                .eq("source_book_id", book.sourceBookId)
                .last("LIMIT 1"));
        if (mapping == null) {
            mapping = new NovelSourceMapping();
            mapping.createdAt = now;
        }
        mapping.identityId = identity.id;
        mapping.novelId = identity.novelId;
        mapping.sourceCode = book.sourceCode;
        mapping.sourceBookId = book.sourceBookId;
        mapping.sourceUrl = limit(book.sourceUrl, 512);
        mapping.sourceTitle = limit(book.title, 128);
        mapping.sourceAuthor = limit(StringUtils.hasText(book.author) ? book.author : "未知作者", 64);
        mapping.contentStatus = StringUtils.hasText(book.contentStatus) ? book.contentStatus : "META_ONLY";
        mapping.matchStatus = "MATCHED";
        mapping.confidenceScore = 100;
        mapping.lastCrawledAt = book.crawledAt;
        mapping.updatedAt = now;
        if (mapping.id == null) {
            novelSourceMappingMapper.insert(mapping);
        } else {
            novelSourceMappingMapper.updateById(mapping);
        }
        return mapping;
    }

    private Novel ensureNovel(NovelIdentity identity, NovelSourceMapping mapping, CrawlBookRaw book, LocalDateTime now) {
        Novel novel = identity.novelId == null ? null : novelMapper.selectById(identity.novelId);
        if (novel == null) {
            novel = novelMapper.selectOne(new QueryWrapper<Novel>()
                    .eq("title", book.title)
                    .eq("author", StringUtils.hasText(book.author) ? book.author : "未知作者")
                    .last("LIMIT 1"));
        }
        if (novel == null) {
            novel = new Novel();
            novel.setCreatedAt(now);
        }
        novel.setTitle(limit(book.title, 128));
        novel.setAuthor(limit(StringUtils.hasText(book.author) ? book.author : "未知作者", 64));
        novel.setCoverUrl(limit(book.coverUrl, 512));
        novel.setIntro(book.intro);
        novel.setCategoryId(1L);
        novel.setStatus("COMPLETED".equals(book.bookStatus) ? 2 : 1);
        novel.setVipRequired(false);
        novel.setFreeChapterCount(999999);
        novel.setWordCount(book.wordCount == null ? 0L : book.wordCount);
        novel.setSourceUrl(limit(book.sourceUrl, 512));
        novel.setUpdatedAt(now);
        if (novel.getId() == null) {
            novelMapper.insert(novel);
        } else {
            novelMapper.updateById(novel);
        }
        identity.novelId = novel.getId();
        identity.updatedAt = now;
        identityMapper.updateById(identity);
        mapping.novelId = novel.getId();
        novelSourceMappingMapper.updateById(mapping);
        return novel;
    }

    private Chapter upsertChapter(Novel novel, CrawlChapterRaw rawChapter, String content, LocalDateTime now) {
        Chapter chapter = chapterMapper.selectOne(new QueryWrapper<Chapter>()
                .eq("novel_id", novel.getId())
                .eq("chapter_no", rawChapter.chapterNo)
                .last("LIMIT 1"));
        if (chapter == null) {
            chapter = new Chapter();
            chapter.setNovelId(novel.getId());
            chapter.setChapterNo(rawChapter.chapterNo);
            chapter.setCreatedAt(now);
        }
        chapter.setTitle(limit(rawChapter.title, 255));
        chapter.setContent(content);
        chapter.setVip(rawChapter.vip != null && rawChapter.vip);
        chapter.setPriceCoin(rawChapter.priceCoin == null ? 0 : rawChapter.priceCoin);
        chapter.setSourceUrl(limit(rawChapter.sourceUrl, 512));
        chapter.setUpdatedAt(now);
        if (chapter.getId() == null) {
            chapterMapper.insert(chapter);
        } else {
            chapterMapper.updateById(chapter);
        }
        return chapter;
    }

    private void upsertChapterMapping(NovelSourceMapping novelMapping, CrawlChapterRaw rawChapter, Long chapterId,
                                      String status, LocalDateTime now) {
        ChapterSourceMapping mapping = chapterSourceMappingMapper.selectOne(new QueryWrapper<ChapterSourceMapping>()
                .eq("novel_mapping_id", novelMapping.id)
                .eq("source_chapter_id", rawChapter.sourceChapterId)
                .last("LIMIT 1"));
        if (mapping == null) {
            mapping = new ChapterSourceMapping();
            mapping.createdAt = now;
        }
        mapping.novelMappingId = novelMapping.id;
        mapping.chapterId = chapterId;
        mapping.sourceChapterId = rawChapter.sourceChapterId;
        mapping.sourceUrl = limit(rawChapter.sourceUrl, 512);
        mapping.sourceTitle = limit(rawChapter.title, 255);
        mapping.chapterNo = rawChapter.chapterNo;
        mapping.vip = rawChapter.vip != null && rawChapter.vip;
        mapping.contentHash = rawChapter.contentHash;
        mapping.contentStatus = status;
        mapping.updatedAt = now;
        if (mapping.id == null) {
            chapterSourceMappingMapper.insert(mapping);
        } else {
            chapterSourceMappingMapper.updateById(mapping);
        }
    }

    private void refreshNovelLatestChapter(Novel novel) {
        Chapter latest = chapterMapper.selectOne(new QueryWrapper<Chapter>()
                .eq("novel_id", novel.getId())
                .orderByDesc("chapter_no")
                .last("LIMIT 1"));
        if (latest != null) {
            novel.setLatestChapterId(latest.getId());
            novel.setLatestChapterTitle(latest.getTitle());
            novel.setUpdatedAt(LocalDateTime.now());
            novelMapper.updateById(novel);
        }
    }

    private void upsertMergeItem(CrawlMergeTask task, CrawlBookRaw book, NovelIdentity identity, Novel novel,
                                 String status, String message) {
        CrawlMergeItem item = mergeItemMapper.selectOne(new QueryWrapper<CrawlMergeItem>()
                .eq("merge_task_id", task.id)
                .eq("book_raw_id", book.id)
                .last("LIMIT 1"));
        LocalDateTime now = LocalDateTime.now();
        if (item == null) {
            item = new CrawlMergeItem();
            item.mergeTaskId = task.id;
            item.bookRawId = book.id;
            item.createdAt = now;
        }
        item.identityId = identity.id;
        item.novelId = novel.getId();
        item.matchStatus = status;
        item.confidenceScore = 100;
        item.message = limit(message, 1000);
        item.updatedAt = now;
        if (item.id == null) {
            mergeItemMapper.insert(item);
        } else {
            mergeItemMapper.updateById(item);
        }
    }

    private ContentQuality evaluateContent(String content) {
        if (!StringUtils.hasText(content)) {
            return new ContentQuality(false, "正文为空");
        }
        String text = content.trim();
        if (text.length() < MIN_CONTENT_LENGTH) {
            return new ContentQuality(false, "正文长度不足");
        }
        if (containsBlockedText(text)) {
            return new ContentQuality(false, "疑似登录/付费/防盗提示");
        }
        long chineseCount = text.chars().filter(ch -> ch >= 0x4E00 && ch <= 0x9FFF).count();
        double ratio = chineseCount * 1.0 / Math.max(text.length(), 1);
        if (ratio < 0.35) {
            return new ContentQuality(false, "中文正文比例不足");
        }
        return new ContentQuality(true, "OK");
    }

    private boolean containsBlockedText(String text) {
        return text.contains("登录") || text.contains("付费") || text.contains("订阅")
                || text.contains("VIP") || text.contains("本章未完") || text.contains("请收藏");
    }

    private String normalizeIdentity(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.toLowerCase()
                .replaceAll("[\\s　《》<>【】\\[\\]（）()：:，,。.!！?？'\"“”‘’_-]", "")
                .trim();
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private enum MergeOutcome {
        MERGED,
        PENDING_REVIEW,
        FAILED
    }

    private record ContentQuality(boolean accepted, String message) {
    }
}
