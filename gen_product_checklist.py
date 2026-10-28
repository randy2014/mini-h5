# -*- coding: utf-8 -*-
"""Generate mini-h5 product checklist as a real .xlsx (no third-party deps)."""
import zipfile

OUT = r"F:\AI-PROD\mini-h5\mini-h5产品功能清单_v2.xlsx"


def esc(s):
    return (str(s)
            .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace('"', "&quot;"))


def col_letter(n):
    s = ""
    while n > 0:
        n, r = divmod(n - 1, 26)
        s = chr(65 + r) + s
    return s


def cell(ref, value, style=None):
    if isinstance(value, int):
        s = f' s="{style}"' if style is not None else ""
        return f'<c r="{ref}"{s}><v>{value}</v></c>'
    s = f' s="{style}"' if style is not None else ""
    return (f'<c r="{ref}"{s} t="inlineStr"><is>'
            f'<t xml:space="preserve">{esc(value)}</t></is></c>')


def build_sheet(data, widths):
    parts = []
    if widths:
        cols = "".join(
            f'<col min="{i+1}" max="{i+1}" width="{w}" customWidth="1"/>'
            for i, w in enumerate(widths))
        parts.append(f"<cols>{cols}</cols>")

    rows = []
    header = data[0]
    rows.append("<row>" + "".join(
        cell(f"{col_letter(j+1)}1", header[j], style=1)
        for j in range(len(header))) + "</row>")
    for i, r in enumerate(data[1:]):
        rn = i + 2
        rows.append("<row>" + "".join(
            cell(f"{col_letter(j+1)}{rn}", r[j], style=2)
            for j in range(len(r))) + "</row>")
    parts.append("<sheetData>" + "".join(rows) + "</sheetData>")

    return ('<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
            '<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">'
            + "".join(parts) + "</worksheet>")


# ---- data ----
overview = [
    ["模块", "类型", "优先级", "落地阶段", "功能项数"],
    ["一、订阅核心", "新增", "P0", "M1 第一批", 6],
    ["二、新会员试用", "新增", "P0", "M1 第一批", 2],
    ["三、快乐币体系", "新增", "P0", "M1 第一批", 3],
    ["四、订阅频道管理", "新增", "P0", "M1 第一批", 1],
    ["五、精品内容机制", "改造+新增", "P1", "M2 第二批", 3],
    ["六、阅读体验", "改造", "P2", "M3 第三批", 1],
    ["七、数据埋点", "新增", "P2", "M3 第三批", 1],
    ["八、支付", "新增(后置)", "P3", "M4 第四批", 1],
]

detail = [
    ["模块", "编号", "功能项", "类型", "优先级", "落地阶段", "描述", "验收标准", "依赖关系", "红线/备注"],
    ["一、订阅核心", "1.1", "订阅关系模型", "新增", "P0", "M1",
     "建立「用户×分类×周期」的订阅关系", "能查询某用户订阅了哪些分类、什么周期、何时到期", "无（基础）", ""],
    ["一、订阅核心", "1.2", "订阅操作", "新增", "P0", "M1",
     "订阅/续订/取消/查询；快乐币足够时一键订阅并扣币", "用户能订阅、续订、取消、查看；余额足够可一键订阅扣币", "依赖 1.1 + 3.1", ""],
    ["一、订阅核心", "1.3", "周期管理", "新增", "P0", "M1",
     "1周/1月/1季/1年 四种周期", "选不同周期，到期时间计算正确", "依赖 1.1", ""],
    ["一、订阅核心", "1.4", "到期自动回收", "新增", "P0", "M1",
     "定时扫描到期订阅，回收访问权", "到期后无法再访问该分类", "依赖 1.1 + 1.3", "红线：回收≠自动扣费"],
    ["一、订阅核心", "1.5", "订阅访问控制", "新增", "P0", "M1",
     "付费正文接口校验「是否订阅了该分类」", "未订阅被拦截，已订阅正常访问", "依赖 1.1", ""],
    ["一、订阅核心", "1.6", "订阅频道tab入口", "新增", "P0", "M1",
     "底部tab新增「订阅频道」，三态展示", "普通=暂无权限；新用户1周=全开；订阅用户=看已订阅", "依赖 1.1 + 1.5 + 2.1", "5个tab已达移动端上限"],
    ["二、新会员试用", "2.1", "1周全开", "新增", "P0", "M1",
     "VIP激活时自动开通全部分类的1周临时访问权", "新VIP激活后1周内可访问所有付费分类", "依赖 1.5", ""],
    ["二、新会员试用", "2.2", "试用到期回收", "新增", "P0", "M1",
     "1周到期自动回收全部访问权", "到期后付费分类全部不可访问", "依赖 2.1", "合规红线：回收必须彻底(成人内容)"],
    ["三、快乐币体系", "3.1", "快乐币余额+流水", "新增", "P0", "M1",
     "快乐币余额表 + 流水表", "能记录和查询余额及每笔变动", "无（基础）", ""],
    ["三、快乐币体系", "3.2", "后台充值(临时)", "新增", "P0", "M1",
     "管理员后台给指定用户充快乐币并留痕", "后台能给指定用户充值、记录原因和操作人", "依赖 3.1",
     "冷启动临时权宜：限邀请码赠送/活动补偿/内测发放"],
    ["三、快乐币体系", "3.3", "C端查币+充值履历", "新增", "P0", "M1",
     "我的页面查看快乐币余额 + 充值履历", "用户能看到余额和每笔充值记录", "依赖 3.1 + 3.2", ""],
    ["四、订阅频道管理", "4.1", "订阅频道分类管理", "新增", "P0", "M1",
     "后台管理订阅频道的分类(增删改查)", "后台能增删改查订阅频道分类", "vip_category 已有基础", ""],
    ["五、精品内容机制", "5.1", "精品榜采集", "改造", "P1", "M2",
     "采集源从lastupdate改为完本/热门/推荐榜", "采集内容以完本、高热度为主", "无（爬虫侧独立改造）", ""],
    ["五、精品内容机制", "5.2", "质量评分", "新增", "P1", "M2",
     "字数/完本/错别字/广告/章节完整的0-100负向过滤", "低于阈值的内容不进分类", "依赖 5.1", ""],
    ["五、精品内容机制", "5.3", "分类归位", "新增", "P1", "M2",
     "源分类→业务分类映射归一化+多信号判定+低置信度人工复核", "内容准确入分类，低置信度进人工复核", "依赖 5.1 + 分类表(已有)", ""],
    ["六、阅读体验", "6.1", "阅读历史API", "改造", "P2", "M3",
     "user_read_history表接API，进度迁到账户级", "换设备能恢复阅读进度", "无（独立）", ""],
    ["七、数据埋点", "7.1", "核心埋点", "新增", "P2", "M3",
     "完读率/订阅转化/分类留存/阅读时长", "能算出每个分类的完读率、订阅转化率", "基建可先行；转化埋点依赖 1.x/5.x", "精品标准由读者数据接管的前提"],
    ["八、支付", "8.1", "自助充值支付", "新增(后置)", "P3", "M4",
     "用户自助购买快乐币（支付闭环）", "用户能自助充值买币", "依赖 3.1", "冷启动临时权宜的终结：让快乐币回到真实货币锚定"],
]

plan = [
    ["里程碑", "编号", "功能项", "依赖/说明"],
    ["M1 第一批·模式跑通", "1.1", "订阅关系模型", "无（基础）"],
    ["M1 第一批·模式跑通", "1.2", "订阅操作", "依赖 1.1 + 3.1"],
    ["M1 第一批·模式跑通", "1.3", "周期管理", "依赖 1.1"],
    ["M1 第一批·模式跑通", "1.4", "到期自动回收", "依赖 1.1 + 1.3"],
    ["M1 第一批·模式跑通", "1.5", "订阅访问控制", "依赖 1.1"],
    ["M1 第一批·模式跑通", "1.6", "订阅频道tab入口", "依赖 1.1 + 1.5 + 2.1"],
    ["M1 第一批·模式跑通", "2.1", "1周全开", "依赖 1.5"],
    ["M1 第一批·模式跑通", "2.2", "试用到期回收", "依赖 2.1"],
    ["M1 第一批·模式跑通", "3.1", "快乐币余额+流水", "无（基础）"],
    ["M1 第一批·模式跑通", "3.2", "后台充值(临时)", "依赖 3.1；限邀请码赠送/活动补偿/内测"],
    ["M1 第一批·模式跑通", "3.3", "C端查币+充值履历", "依赖 3.1 + 3.2"],
    ["M1 第一批·模式跑通", "4.1", "订阅频道分类管理", "vip_category 已有基础"],
    ["M2 第二批·内容命门", "5.1", "精品榜采集", "无（爬虫侧独立改造）"],
    ["M2 第二批·内容命门", "5.2", "质量评分", "依赖 5.1"],
    ["M2 第二批·内容命门", "5.3", "分类归位", "依赖 5.1 + 分类表(已有)"],
    ["M3 第三批·体验与数据", "6.1", "阅读历史API", "无（独立）"],
    ["M3 第三批·体验与数据", "7.1", "核心埋点", "埋点基建可先行；转化埋点依赖 1.x/5.x"],
    ["M4 第四批·变现", "8.1", "自助充值支付", "依赖 3.1；冷启动临时权宜的终结"],
]

# ---- build xlsx parts ----
content_types = ('<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
                 '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">'
                 '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>'
                 '<Default Extension="xml" ContentType="application/xml"/>'
                 '<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>'
                 '<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>'
                 '<Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>'
                 '<Override PartName="/xl/worksheets/sheet3.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>'
                 '<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>'
                 '</Types>')

rels = ('<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
        '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
        '<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>'
        '</Relationships>')

workbook = ('<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
            '<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" '
            'xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">'
            '<sheets>'
            '<sheet name="总览" sheetId="1" r:id="rId1"/>'
            '<sheet name="功能清单" sheetId="2" r:id="rId2"/>'
            '<sheet name="落地计划" sheetId="3" r:id="rId3"/>'
            '</sheets></workbook>')

workbook_rels = ('<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
                 '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
                 '<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>'
                 '<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>'
                 '<Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet3.xml"/>'
                 '<Relationship Id="rId4" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>'
                 '</Relationships>')

styles = ('<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
          '<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">'
          '<fonts count="2">'
          '<font><sz val="11"/><name val="Calibri"/></font>'
          '<font><b/><sz val="11"/><name val="Calibri"/></font>'
          '</fonts>'
          '<fills count="3">'
          '<fill><patternFill patternType="none"/></fill>'
          '<fill><patternFill patternType="gray125"/></fill>'
          '<fill><patternFill patternType="solid"><fgColor rgb="FFDDEBF7"/><bgColor indexed="64"/></patternFill></fill>'
          '</fills>'
          '<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>'
          '<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>'
          '<cellXfs count="3">'
          '<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>'
          '<xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/>'
          '<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0" applyAlignment="1"><alignment vertical="top" wrapText="1"/></xf>'
          '</cellXfs></styleSheet>')

sheet1 = build_sheet(overview, [18, 12, 10, 14, 12])
sheet2 = build_sheet(detail, [16, 6, 14, 12, 8, 10, 40, 32, 26, 24])
sheet3 = build_sheet(plan, [22, 6, 16, 42])

with zipfile.ZipFile(OUT, "w", zipfile.ZIP_DEFLATED) as z:
    z.writestr("[Content_Types].xml", content_types)
    z.writestr("_rels/.rels", rels)
    z.writestr("xl/workbook.xml", workbook)
    z.writestr("xl/_rels/workbook.xml.rels", workbook_rels)
    z.writestr("xl/styles.xml", styles)
    z.writestr("xl/worksheets/sheet1.xml", sheet1)
    z.writestr("xl/worksheets/sheet2.xml", sheet2)
    z.writestr("xl/worksheets/sheet3.xml", sheet3)

print("written:", OUT)
