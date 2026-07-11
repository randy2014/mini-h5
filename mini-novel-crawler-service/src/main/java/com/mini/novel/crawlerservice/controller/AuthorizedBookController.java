package com.mini.novel.crawlerservice.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mini.novel.common.result.Result;
import com.mini.novel.crawler.entity.*;
import com.mini.novel.crawler.mapper.*;
import com.mini.novel.crawlerservice.service.AuthorizedBookPolicy;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/crawler/authorized-books")
public class AuthorizedBookController {
 private final CrawlerAuthorizedBookMapper books; private final CrawlerAuthorizedBookAuditMapper audits;
 private final CrawlerSourceConfigMapper sources; private final CrawlRankSourceMapper ranks; private final ObjectMapper json;
 public AuthorizedBookController(CrawlerAuthorizedBookMapper b,CrawlerAuthorizedBookAuditMapper a,CrawlerSourceConfigMapper s,CrawlRankSourceMapper r,ObjectMapper j){books=b;audits=a;sources=s;ranks=r;json=j;}
 @GetMapping public Result<Page<CrawlerAuthorizedBook>> list(@RequestParam(defaultValue="1") long page,@RequestParam(defaultValue="20") long size,@RequestParam(required=false) String keyword,@RequestParam(required=false) String sourceCode,@RequestParam(required=false) String authorizationStatus,@RequestParam(required=false) String reviewStatus,@RequestParam(required=false) String riskLevel){
  QueryWrapper<CrawlerAuthorizedBook> q=new QueryWrapper<>(); if(StringUtils.hasText(keyword))q.and(w->w.like("title",keyword).or().like("author",keyword).or().like("source_book_id",keyword)); eq(q,"source_code",sourceCode);eq(q,"authorization_status",authorizationStatus);eq(q,"review_status",reviewStatus);eq(q,"risk_level",riskLevel);q.orderByDesc("updated_at","id");return Result.ok(books.selectPage(new Page<>(Math.max(1,page),Math.min(100,Math.max(1,size))),q));}
 @GetMapping("/{id}") public Result<Map<String,Object>> detail(@PathVariable Long id){CrawlerAuthorizedBook b=books.selectById(id);if(b==null)return new Result<>(404,"Authorized book does not exist.",null);Map<String,Object> m=new LinkedHashMap<>();m.put("book",b);m.put("audits",audits.selectList(new QueryWrapper<CrawlerAuthorizedBookAudit>().eq("authorized_book_id",id).orderByDesc("created_at","id").last("LIMIT 200")));return Result.ok(m);}
 @PutMapping("/{id}") @Transactional public Result<CrawlerAuthorizedBook> update(@PathVariable Long id,@RequestBody Change c,@RequestHeader(value="X-Operator-Id",defaultValue="0")Long op){return apply(id,c,op);}
 @PostMapping("/batch") @Transactional public Result<List<CrawlerAuthorizedBook>> batch(@RequestBody BatchChange r,@RequestHeader(value="X-Operator-Id",defaultValue="0")Long op){if(r.ids==null||r.ids.isEmpty())return new Result<>(400,"Select at least one book.",null);List<CrawlerAuthorizedBook> out=new ArrayList<>();for(Long id:r.ids){Result<CrawlerAuthorizedBook>x=apply(id,r.change,op);if(x.code()!=0)throw new IllegalArgumentException(x.message());out.add(x.data());}return Result.ok(out);}
 @PostMapping("/collect-metadata") public Result<Void> collect(){CrawlerSourceConfig s=sources.selectOne(new QueryWrapper<CrawlerSourceConfig>().eq("source_code","xbookcn_authorized").last("LIMIT 1"));if(s==null||!Boolean.TRUE.equals(s.enabled))return new Result<>(409,"xbookcn source is disabled; metadata collection was not started.",null);long n=ranks.selectCount(new QueryWrapper<CrawlRankSource>().eq("source_id",s.id).eq("enabled",true));if(n==0)return new Result<>(409,"xbookcn rank is disabled; metadata collection was not started.",null);return new Result<>(409,"Metadata trigger remains gated; no full-site chapter crawl is available.",null);}
 private Result<CrawlerAuthorizedBook> apply(Long id,Change c,Long op){CrawlerAuthorizedBook before=books.selectById(id);if(before==null)return new Result<>(404,"Authorized book does not exist.",null);if(c==null)return new Result<>(400,"Change payload is required.",null);CrawlerAuthorizedBook after=json.convertValue(before,CrawlerAuthorizedBook.class);if(c.authorizationStatus!=null)after.authorizationStatus=c.authorizationStatus;if(c.reviewStatus!=null)after.reviewStatus=c.reviewStatus;if(c.riskLevel!=null)after.riskLevel=c.riskLevel;if(c.riskReason!=null)after.riskReason=c.riskReason;if(c.authorizationNote!=null)after.authorizationNote=c.authorizationNote;if(c.proofRef!=null)after.proofRef=c.proofRef;if(c.allowCrawlChapters!=null)after.allowCrawlChapters=c.allowCrawlChapters;if(c.allowStore!=null)after.allowStore=c.allowStore;if(c.allowDisplay!=null)after.allowDisplay=c.allowDisplay;if(c.allowVipDisplay!=null)after.allowVipDisplay=c.allowVipDisplay;AuthorizedBookPolicy.validate(after);LocalDateTime now=LocalDateTime.now();after.updatedAt=now;if(!Objects.equals(before.authorizationStatus,after.authorizationStatus)){after.authorizedBy=op;after.authorizedAt=now;}if(!Objects.equals(before.reviewStatus,after.reviewStatus)){after.reviewedBy=op;after.reviewedAt=now;}books.updateById(after);CrawlerAuthorizedBookAudit a=new CrawlerAuthorizedBookAudit();a.authorizedBookId=id;a.action=c.action==null?"UPDATE":c.action;a.operatorId=op;a.remark=c.remark;a.createdAt=now;try{a.beforeJson=json.writeValueAsString(before);a.afterJson=json.writeValueAsString(after);}catch(Exception e){throw new IllegalStateException(e);}audits.insert(a);return Result.ok(after);}
 private static void eq(QueryWrapper<?>q,String c,String v){if(StringUtils.hasText(v))q.eq(c,v);}
 public static class Change{public String authorizationStatus,reviewStatus,riskLevel,riskReason,authorizationNote,proofRef,action,remark;public Boolean allowCrawlChapters,allowStore,allowDisplay,allowVipDisplay;}
 public static class BatchChange{public List<Long> ids;public Change change;}
 @ExceptionHandler(IllegalArgumentException.class) @ResponseStatus(org.springframework.http.HttpStatus.BAD_REQUEST)
 public Result<Void> invalid(IllegalArgumentException e){return Result.fail(400,e.getMessage());}
}
