# 快速答疑 (Quick Answers)

> 针对用户两个核心问题的直观回答

---

## 🏷️ 问题1：现在你识别标题是通过什么标识？

### 简答

转换器支持**两种方式**识别标题：

```
方式1: HeadingBlock类型 ────────────> 直接识别
方式2: ParagraphBlock样式名 ────────> 自动检测
```

---

### 方式1：HeadingBlock（直接识别）

```java
// 如果您的模型是这样的：
HeadingBlock heading = new HeadingBlock();
heading.setLevel(2);        // 设置级别
heading.setText("第一章");

// ↓ 转换器直接识别

{
  "type": "heading",
  "attrs": {
    "level": 1,           // TipTap级别 (自动映射)
    "originLevel": 2      // 原始级别 (保留)
  }
}
```

**触发条件**：`block instanceof HeadingBlock`  
**代码位置**：`TiptapConverter.java` 第197-222行

---

### 方式2：样式名检测（自动识别）

```java
// 如果您的模型是这样的：
ParagraphBlock para = new ParagraphBlock();
para.setStyle("Heading 2");  // 设置Word样式名
para.setRuns(...);

// ↓ 转换器自动检测样式名

{
  "type": "heading",        // 自动识别为标题
  "attrs": {
    "level": 2,
    "styleSource": "Heading 2"  // 保留样式名
  }
}
```

**触发条件**：`ParagraphBlock.style` 匹配标题模式  
**代码位置**：`TiptapConverter.java` 第228-310行

---

### 支持的样式名（中英文）

| 样式名示例 | 识别级别 | 说明 |
|-----------|---------|------|
| `Heading 1` | 1 | 英文标准 |
| `Heading1` | 1 | 无空格 |
| `标题 1` | 1 | 中文标准 |
| `标题1` | 1 | 无空格 |
| `Title` | 1 | 特殊：标题 |
| `Subtitle` | 2 | 特殊：副标题 |
| `副标题` | 2 | 中文副标题 |
| `Heading 2-6` | 2-6 | 自动提取数字 |

**检测逻辑**：
```java
// 正则匹配
if (styleName.matches(".*heading\\s*[1-6].*") ||
    styleName.matches(".*标题\\s*[1-6].*")) {
    // 提取数字1-6
}

// 特殊情况
if (styleName.contains("title") || styleName.equals("标题")) return 1;
if (styleName.contains("subtitle")) return 2;
```

---

### 优先级

```
HeadingBlock直接识别 (高优先级)
         ↓
ParagraphBlock样式检测 (中优先级)
         ↓
普通ParagraphBlock (无标题)
```

---

## 📎 问题2：原逻辑解析后会上传附件和图片，你是怎么处理的？

### 简答

转换器**不负责上传**，只做数据转换！

```
┌─────────────┐    ┌─────────────┐    ┌─────────────┐
│  解析Word   │ →  │  上传媒体   │ →  │ TipTap转换  │
│  (您的代码) │    │  (您的代码) │    │  (转换器)   │
└─────────────┘    └─────────────┘    └─────────────┘
     Apache POI         MinIO/OSS          本项目
     提取数据           生成URL            只转换
```

---

### 职责分工

| 阶段 | 负责方 | 具体操作 |
|------|--------|---------|
| ① Word解析 | 您的代码 | 使用Apache POI提取图片/附件的二进制数据 |
| ② 媒体上传 | 您的代码 | 调用上传服务（MinIO/OSS），获得URL |
| ③ 构建模型 | 您的代码 | 将URL写入DocumentData对象 |
| ④ JSON转换 | 转换器 | 读取URL，原样输出到TipTap JSON |

---

### 对比：传统代码 vs 新转换器

#### 传统代码（您的原逻辑）

```java
// DocWordServiceImpl.java
private void setPictures(XWPFRun run, ...) {
    List<XWPFPicture> pictures = run.getEmbeddedPictures();
    for (XWPFPicture pic : pictures) {
        // ① 提取二进制
        byte[] data = pic.getPictureData().getData();
        
        // ② 立即上传
        AttachDto result = documentAttachService.upload(...);
        
        // ③ 使用上传后的URL
        imageNode.put("src", result.getFilePath());
    }
}
```

**特点**：解析、上传、转换混在一起

---

#### 新转换器（职责分离）

```java
// TiptapConverter.java
private Map<String, Object> convertImageBlock(Object block) {
    // 假设URL已存在
    String src = getFieldValue(block, "getSrc");
    
    // 原样输出，不做上传
    attrs.put("src", src);
    return node;
}
```

**特点**：只做转换，不涉及IO

---

### 推荐的集成方式

```java
public String importWord(MultipartFile file, String docId) {
    // ━━━ 第1步：解析 + 上传 ━━━
    DocumentData data = parseAndUpload(file, docId);
    
    // ━━━ 第2步：转换 ━━━
    TiptapConverter converter = new TiptapConverter();
    Map<String, Object> json = converter.convert(data);
    
    // ━━━ 第3步：保存 ━━━
    save(json);
}

private DocumentData parseAndUpload(MultipartFile file, String docId) {
    XWPFDocument doc = new XWPFDocument(file.getInputStream());
    DocumentData data = new DocumentData();
    
    for (XWPFParagraph para : doc.getParagraphs()) {
        ParagraphBlock block = new ParagraphBlock();
        
        // ★ 设置样式名（供标题检测）
        block.setStyle(para.getStyleID());
        
        for (XWPFRun run : para.getRuns()) {
            TextRun textRun = new TextRun();
            
            // ★ 处理图片：立即上传
            for (XWPFPicture pic : run.getEmbeddedPictures()) {
                String url = uploadImage(pic.getData(), docId);
                EmbeddedImage img = new EmbeddedImage();
                img.setSrc(url);  // ★ 保存URL
                textRun.getEmbeddedImages().add(img);
            }
            
            block.getRuns().add(textRun);
        }
        
        data.getBlocks().add(block);
    }
    
    return data;  // URL已在模型中
}

private String uploadImage(byte[] data, String docId) {
    MultipartFile file = new MockMultipartFile(...);
    AttachDto result = uploadService.upload(file, docId);
    return result.getFilePath();  // 返回URL
}
```

---

### 关键点

#### ✅ 正确做法

```java
// 在构建DocumentData时上传
EmbeddedImage img = new EmbeddedImage();
img.setSrc("https://cdn.com/img1.jpg");  // ← 已上传的URL

// 转换器直接使用
converter.convert(documentData);
// 输出: {"type": "image", "attrs": {"src": "https://cdn.com/img1.jpg"}}
```

#### ❌ 错误做法

```java
// 不要在模型中放临时路径
EmbeddedImage img = new EmbeddedImage();
img.setSrc("/tmp/img1.jpg");  // ← 错误：临时路径

// 转换器不会上传
converter.convert(documentData);
// 输出: {"type": "image", "attrs": {"src": "/tmp/img1.jpg"}}  // 无效URL！
```

---

### 为什么这样设计？

| 优点 | 说明 |
|------|------|
| 🎯 **职责单一** | 转换器只做转换，不做IO |
| ✅ **易于测试** | 无需Mock上传服务 |
| 🔄 **灵活性高** | 可以自由选择上传服务 |
| 🚀 **性能可控** | 您决定何时、如何上传 |
| 🛡️ **安全可靠** | 转换器不涉及凭证管理 |

---

## 📊 对比总结

### 标题识别

```
┌───────────────────────────────────────────────────┐
│  输入                           输出              │
├───────────────────────────────────────────────────┤
│  HeadingBlock (level=2)    →   heading (level=1) │
│  ParagraphBlock ("标题 1")  →   heading (level=1) │
│  ParagraphBlock ("Heading2")→   heading (level=2) │
│  ParagraphBlock (无样式)     →   paragraph        │
└───────────────────────────────────────────────────┘
```

### 媒体处理

```
┌───────────────────────────────────────────────────┐
│  阶段          操作            负责方              │
├───────────────────────────────────────────────────┤
│  解析         提取二进制       您的代码            │
│  上传         存储+生成URL     您的代码            │
│  转换         URL→JSON        转换器              │
│  保存         持久化          您的代码            │
└───────────────────────────────────────────────────┘
```

---

## 🔗 相关文档

完整详细说明请查看：

- **FAQ.md** - 详细问答（17,901字符）
- **LEGACY_MAPPING.md** - 传统代码映射
- **ENHANCEMENTS.md** - 增强功能说明
- **INTEGRATION.md** - 集成指南

---

## 💡 快速记忆

### 标题识别

```
记住：两种方式
1. HeadingBlock → 直接认
2. 样式名检测 → 自动认
```

### 媒体处理

```
记住：职责分离
转换器不上传，只转换
URL要提前准备好
```

---

**问题已解答！** 如有其他疑问，请查看完整FAQ文档。
