# 常见问题解答 (FAQ)

## 问题1：现在你识别标题是通过什么标识？

### 回答：转换器支持**两种**标题识别方式

#### 方式一：HeadingBlock类型识别（推荐）

如果您的DocumentData模型中已经明确区分了HeadingBlock，转换器会直接识别：

```java
// 模型中的HeadingBlock
public class HeadingBlock extends ContentBlock {
    private Integer level;  // 标题级别：1-9
    private String text;
}

// 转换为TipTap
{
  "type": "heading",
  "attrs": {
    "level": 1,              // TipTap级别：1-6 (自动映射)
    "originLevel": 2         // 原始级别：保留完整信息
  },
  "content": [...]
}
```

**级别映射规则**：
- TipTap只支持1-6级标题
- 映射公式：`tiptapLevel = max(1, min(6, originLevel - 1))`
- 原始级别保存在`originLevel`字段，确保无损

**代码位置**：`TiptapConverter.java` 第197-222行
```java
private Map<String, Object> convertHeadingBlock(Object block) {
    Integer level = getFieldValue(block, "getLevel");
    int tiptapLevel = Math.max(1, Math.min(6, (level != null ? level : 1) - 1));
    attrs.put("level", tiptapLevel);
    attrs.put("originLevel", level);  // 保留原始值
}
```

---

#### 方式二：ParagraphBlock样式名识别（兼容传统Word样式）

如果您的模型是ParagraphBlock，但包含Word样式信息，转换器会自动检测：

```java
// 模型中的ParagraphBlock
public class ParagraphBlock extends ContentBlock {
    private String style;        // Word样式名
    private List<TextRun> runs;
}

// 如果style包含标题样式名，自动转为heading
```

**支持的样式名模式**：

| 样式名 | 识别为级别 | 正则表达式 |
|--------|-----------|-----------|
| `Heading 1` | 1 | `.*heading\s*[1-6].*` |
| `Heading1` | 1 | 同上 |
| `标题 1` | 1 | `.*标题\s*[1-6].*` |
| `标题1` | 1 | 同上 |
| `Title` | 1 | `contains("title")` |
| `标题` | 1 | `equals("标题")` |
| `Subtitle` | 2 | `contains("subtitle")` |
| `副标题` | 2 | `contains("副标题")` |
| `Heading 2-6` | 2-6 | 从样式名提取数字 |

**代码位置**：`TiptapConverter.java` 第228-275行
```java
private Map<String, Object> convertParagraphBlock(Object block) {
    String paragraphStyle = getFieldValue(block, "getStyle");
    Integer styleLevel = detectHeadingFromStyle(paragraphStyle);
    
    if (styleLevel != null && styleLevel > 0) {
        node.put("type", "heading");  // 转为heading
        attrs.put("styleSource", paragraphStyle);  // 保留样式名
    } else {
        node.put("type", "paragraph");  // 普通段落
    }
}
```

**检测逻辑**：`TiptapConverter.java` 第281-310行
```java
private Integer detectHeadingFromStyle(String styleName) {
    String lowerStyle = styleName.toLowerCase().trim();
    
    // 匹配 "Heading 1" 或 "标题 1"
    if (lowerStyle.matches(".*heading\\s*[1-6].*") || 
        lowerStyle.matches(".*标题\\s*[1-6].*")) {
        // 从样式名提取数字 1-6
        for (char c : lowerStyle.toCharArray()) {
            if (Character.isDigit(c)) {
                return Character.getNumericValue(c);
            }
        }
    }
    
    // 特殊情况
    if (lowerStyle.contains("title") || lowerStyle.equals("标题")) return 1;
    if (lowerStyle.contains("subtitle") || lowerStyle.contains("副标题")) return 2;
    
    return null;  // 不是标题样式
}
```

---

### 输出示例

**输入（HeadingBlock）**：
```java
HeadingBlock {
  level: 2,
  text: "第一章 概述"
}
```

**输出（TipTap JSON）**：
```json
{
  "type": "heading",
  "attrs": {
    "level": 1,
    "originLevel": 2
  },
  "content": [
    {"type": "text", "text": "第一章 概述"}
  ]
}
```

**输入（ParagraphBlock with style）**：
```java
ParagraphBlock {
  style: "Heading 3",
  runs: [{text: "技术架构"}]
}
```

**输出（TipTap JSON）**：
```json
{
  "type": "heading",
  "attrs": {
    "level": 3,
    "originLevel": 3,
    "styleSource": "Heading 3"
  },
  "content": [
    {"type": "text", "text": "技术架构"}
  ]
}
```

---

## 问题2：原逻辑解析后会上传附件和图片，你是怎么处理的？

### 回答：转换器**不负责上传**，只做数据转换

#### 设计理念：职责分离

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│  Word文档解析    │ →  │  TipTap转换器   │ →  │  业务层上传     │
│  (Apache POI)   │    │  (本项目)       │    │  (您的代码)     │
├─────────────────┤    ├─────────────────┤    ├─────────────────┤
│ XWPFDocument    │    │ DocumentData    │    │ MinIO/OSS       │
│ XWPFPicture     │    │ → TipTap JSON   │    │ 文件上传服务     │
│ OLE Objects     │    │ 只转换引用       │    │ URL生成         │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

**核心原则**：
1. **转换器不做IO操作** - 不读文件、不写文件、不上传网络
2. **只处理已有数据** - 假设图片/附件的URL已存在于模型中
3. **完整保留引用** - 将所有路径/URL信息无损转换到JSON
4. **由调用方决定** - 何时上传、上传到哪、如何生成URL

---

### 处理方式详解

#### 1. 图片处理

转换器期望DocumentData中的图片**已经包含URL**：

**模型结构**：
```java
// 方式A：独立的ImageBlock
public class ImageBlock extends ContentBlock {
    private String src;    // 图片URL（已上传后的地址）
    private String alt;
    private String title;
    private Integer width;
    private Integer height;
}

// 方式B：TextRun中的内嵌图片
public class TextRun {
    private String text;
    private List<EmbeddedImage> embeddedImages;
}

public class EmbeddedImage {
    private String src;    // 图片URL（已上传后的地址）
    private Integer width;
    private Integer height;
}
```

**转换逻辑**：
```java
// 代码位置：TiptapConverter.java 第888-915行
private Map<String, Object> convertImageBlock(Object block) {
    String src = getFieldValue(block, "getSrc");  // 从模型获取URL
    attrs.put("src", src);                        // 原样输出到JSON
    attrs.put("origin", convertToMap(block));     // 完整保留原始数据
    return node;
}
```

**输出JSON**：
```json
{
  "type": "image",
  "attrs": {
    "src": "https://your-cdn.com/images/pic123.jpg",  // 预期已是上传后的URL
    "alt": "示例图片",
    "width": 800,
    "height": 600,
    "origin": {
      "src": "...",
      "alt": "...",
      "uploadedAt": "2024-01-01T00:00:00Z",
      "originalFileName": "example.jpg"
    }
  }
}
```

---

#### 2. 附件处理

同样，转换器期望附件URL已经存在：

**模型结构**：
```java
// 方式A：AttachmentBlock
public class AttachmentBlock extends ContentBlock {
    private String url;      // 附件URL（已上传后的地址）
    private String name;
}

// 方式B：UploadFileBlock (OLE对象)
public class UploadFileBlock extends ContentBlock {
    private String src;      // 文件URL（已上传后的地址）
    private String filename;
    private String filetype;
    private Long filesize;
}
```

**转换逻辑**：
```java
// 代码位置：TiptapConverter.java 第917-929行 (AttachmentBlock)
private Map<String, Object> convertAttachmentBlock(Object block) {
    String url = getFieldValue(block, "getUrl");  // 从模型获取URL
    attrs.put("url", url);                        // 原样输出
    return node;
}

// 代码位置：TiptapConverter.java 第936-987行 (UploadFileBlock)
private Map<String, Object> convertUploadFileBlock(Object block) {
    String src = getFieldValue(block, "getSrc");  // 支持多种字段名
    if (src == null) src = getFieldValue(block, "getUrl");
    if (src == null) src = getFieldValue(block, "getPath");
    
    attrs.put("src", src);
    attrs.put("path", src);
    attrs.put("filename", ...);
    attrs.put("filetype", ...);
    return node;
}
```

**输出JSON**：
```json
{
  "type": "uploadFileNode",
  "attrs": {
    "src": "https://your-cdn.com/files/document.docx",  // 预期已是上传后的URL
    "path": "https://your-cdn.com/files/document.docx",
    "filename": "产品需求文档.docx",
    "filetype": "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "filesize": 102400,
    "origin": {...}
  }
}
```

---

### 与传统代码的对比

#### 传统代码（您的原逻辑）

```java
// DocWordServiceImpl.java - setPictures方法
private void setPictures(XWPFRun run, ArrayNode paragraphContent, String docId, ...) {
    List<XWPFPicture> pictures = run.getEmbeddedPictures();
    for (XWPFPicture xwpfPicture : pictures) {
        XWPFPictureData picture = xwpfPicture.getPictureData();
        
        // ① 创建MultipartFile
        MockMultipartFile multipartFile = new MockMultipartFile(...);
        
        // ② 上传到MinIO/OSS
        AttachDto upload = documentAttachService.upload(multipartFile, docId, 0);
        
        // ③ 使用上传后的URL
        ObjectNode imageNode = mapper.createObjectNode();
        imageNode.put("src", upload.getFilePath());  // 上传后返回的URL
        paragraphContent.add(imageNode);
    }
}

// setFile方法 - 类似逻辑
private void setFile(XWPFRun run, ...) {
    // ① 提取OLE对象的二进制数据
    byte[] data = IOUtils.toByteArray(olePart.getInputStream());
    
    // ② 上传到存储服务
    AttachDto attachDto = docxImportService.covert(data, fileName, docId);
    
    // ③ 使用上传后的URL
    fileAttrNode.put("src", attachDto.getFilePath());
}
```

#### 新转换器（职责分离）

```java
// TiptapConverter.java - 只做转换
private Map<String, Object> convertImageBlock(Object block) {
    // 假设 block.src 已经是上传后的URL
    String src = getFieldValue(block, "getSrc");
    attrs.put("src", src);  // 原样输出，不做上传
    return node;
}
```

---

### 推荐的集成方式

#### 方式A：在解析阶段上传（推荐）

```java
public DocumentData parseWordAndUpload(InputStream wordStream, String docId) {
    XWPFDocument xwpfDoc = new XWPFDocument(wordStream);
    DocumentData documentData = new DocumentData();
    
    for (IBodyElement element : xwpfDoc.getBodyElements()) {
        if (element instanceof XWPFParagraph) {
            XWPFParagraph para = (XWPFParagraph) element;
            ParagraphBlock block = new ParagraphBlock();
            
            // 解析Word样式
            block.setStyle(para.getStyleID());  // 设置样式名供转换器识别标题
            
            for (XWPFRun run : para.getRuns()) {
                TextRun textRun = new TextRun();
                textRun.setText(run.getText(0));
                
                // ★ 处理图片：立即上传
                List<XWPFPicture> pictures = run.getEmbeddedPictures();
                for (XWPFPicture pic : pictures) {
                    // ① 上传图片
                    String uploadedUrl = uploadImage(pic.getPictureData().getData(), docId);
                    
                    // ② 保存URL到模型
                    EmbeddedImage img = new EmbeddedImage();
                    img.setSrc(uploadedUrl);  // 已上传的URL
                    img.setWidth(pic.getWidth());
                    img.setHeight(pic.getHeight());
                    textRun.getEmbeddedImages().add(img);
                }
                
                // ★ 处理附件：立即上传
                // ... 类似逻辑
                
                block.getRuns().add(textRun);
            }
            
            documentData.getBlocks().add(block);
        }
    }
    
    return documentData;  // 返回的DocumentData中已包含所有上传后的URL
}

// 然后使用转换器
public String importWordDocument(MultipartFile file, String docId) {
    // Step 1: 解析Word + 上传媒体文件
    DocumentData documentData = parseWordAndUpload(file.getInputStream(), docId);
    
    // Step 2: 转换为TipTap JSON
    TiptapConverter converter = new TiptapConverter();
    Map<String, Object> tiptapJson = converter.convert(documentData);
    
    // Step 3: 保存JSON
    String json = new ObjectMapper().writeValueAsString(tiptapJson);
    documentService.saveDocument(docId, json);
    
    return json;
}

// 上传辅助方法
private String uploadImage(byte[] imageData, String docId) {
    MultipartFile file = new MockMultipartFile("image", "image.jpg", "image/jpeg", imageData);
    AttachDto result = documentAttachService.upload(file, docId, 0);
    return result.getFilePath();  // 返回上传后的URL
}
```

---

#### 方式B：先转换，后替换URL

```java
public String importWordDocument(MultipartFile file, String docId) {
    // Step 1: 解析Word（暂不上传，使用临时路径）
    DocumentData documentData = parseWordWithTempPaths(file.getInputStream());
    
    // Step 2: 转换为TipTap JSON
    TiptapConverter converter = new TiptapConverter();
    Map<String, Object> tiptapJson = converter.convert(documentData);
    
    // Step 3: 收集所有需要上传的媒体
    List<MediaToUpload> mediaList = collectMediaFromJson(tiptapJson);
    
    // Step 4: 批量上传并替换URL
    for (MediaToUpload media : mediaList) {
        String uploadedUrl = uploadMedia(media.getData(), docId);
        replaceUrlInJson(tiptapJson, media.getTempPath(), uploadedUrl);
    }
    
    // Step 5: 保存JSON
    String json = new ObjectMapper().writeValueAsString(tiptapJson);
    documentService.saveDocument(docId, json);
    
    return json;
}
```

**不推荐此方式**，因为：
- 需要在JSON中查找替换，容易出错
- 临时路径管理复杂
- 不符合转换器的设计理念

---

### 元信息保留

所有图片和附件的**完整信息**都会保留在`attrs.origin`中：

```json
{
  "type": "doc",
  "attrs": {
    "images": [
      {
        "src": "https://cdn.com/img1.jpg",
        "originalFileName": "photo.jpg",
        "uploadedAt": "2024-01-01T10:00:00Z",
        "md5": "abc123...",
        "size": 204800
      }
    ],
    "attachments": [
      {
        "url": "https://cdn.com/file1.docx",
        "name": "需求文档.docx",
        "uploadedAt": "2024-01-01T10:01:00Z",
        "size": 102400
      }
    ]
  },
  "content": [
    {
      "type": "image",
      "attrs": {
        "src": "https://cdn.com/img1.jpg",
        "origin": {
          "src": "https://cdn.com/img1.jpg",
          "originalFileName": "photo.jpg",
          "uploadedAt": "2024-01-01T10:00:00Z",
          "md5": "abc123..."
        }
      }
    }
  ]
}
```

---

## 总结

### 标题识别（问题1）

| 识别方式 | 触发条件 | 优先级 | 代码位置 |
|---------|---------|--------|---------|
| **HeadingBlock** | `block instanceof HeadingBlock` | 🔴 高 | 第197-222行 |
| **样式名检测** | `ParagraphBlock.style` 匹配模式 | 🟡 中 | 第228-310行 |

**关键点**：
- 支持中英文样式名（Heading/标题）
- 级别自动映射到TipTap的1-6
- 原始信息完整保留

### 图片/附件处理（问题2）

| 阶段 | 负责方 | 操作 |
|------|--------|------|
| **解析Word** | 您的代码 | 提取二进制数据 |
| **上传存储** | 您的代码 | 上传到MinIO/OSS，获得URL |
| **构建模型** | 您的代码 | 将URL写入DocumentData |
| **转换JSON** | 转换器 | 读取URL，原样输出 |

**关键点**：
- 转换器**不上传**，只转换
- 预期URL已存在于模型中
- 职责清晰，易于测试和维护

---

## 代码示例：完整流程

```java
@Service
public class DocumentImportService {
    
    @Autowired
    private DocumentAttachService documentAttachService;
    
    @Autowired
    private DocumentService documentService;
    
    /**
     * 完整的Word导入流程
     */
    public String importWordDocument(MultipartFile file, String docId) throws Exception {
        try (InputStream inputStream = file.getInputStream()) {
            // ===== 第一步：解析Word并上传媒体 =====
            DocumentData documentData = parseAndUploadMedia(inputStream, docId);
            
            // ===== 第二步：转换为TipTap JSON =====
            TiptapConverter converter = new TiptapConverter();
            Map<String, Object> tiptapJson = converter.convert(documentData);
            
            // ===== 第三步：保存到数据库 =====
            String json = new ObjectMapper().writeValueAsString(tiptapJson);
            DocDto docDto = new DocDto();
            docDto.setDocId(docId);
            docDto.setContent(json);
            docDto.setTitle(getFileNameWithoutExtension(file.getOriginalFilename()));
            documentService.saveDocument(docDto);
            
            return json;
        }
    }
    
    /**
     * 解析Word文档并上传媒体文件
     */
    private DocumentData parseAndUploadMedia(InputStream inputStream, String docId) throws Exception {
        XWPFDocument xwpfDoc = new XWPFDocument(inputStream);
        DocumentData documentData = new DocumentData();
        
        for (IBodyElement element : xwpfDoc.getBodyElements()) {
            if (element instanceof XWPFParagraph) {
                XWPFParagraph para = (XWPFParagraph) element;
                
                // 创建段落块
                ParagraphBlock block = new ParagraphBlock();
                
                // ★ 关键：设置样式名，供转换器识别标题
                String styleId = para.getStyleID();
                if (styleId != null) {
                    block.setStyle(styleId);  // e.g., "Heading1", "标题 1"
                }
                
                // 设置对齐方式
                if (para.getAlignment() == ParagraphAlignment.CENTER) {
                    block.setAlignment("center");
                } else if (para.getAlignment() == ParagraphAlignment.RIGHT) {
                    block.setAlignment("right");
                }
                
                // 处理runs
                for (XWPFRun run : para.getRuns()) {
                    TextRun textRun = createTextRun(run, docId);
                    block.getRuns().add(textRun);
                }
                
                documentData.getBlocks().add(block);
                
            } else if (element instanceof XWPFTable) {
                TableBlock tableBlock = parseTable((XWPFTable) element);
                documentData.getBlocks().add(tableBlock);
            }
        }
        
        return documentData;
    }
    
    /**
     * 创建TextRun并上传其中的媒体
     */
    private TextRun createTextRun(XWPFRun run, String docId) throws Exception {
        TextRun textRun = new TextRun();
        textRun.setText(run.getText(0));
        textRun.setBold(run.isBold());
        textRun.setItalic(run.isItalic());
        textRun.setFontFamily(run.getFontFamily());
        textRun.setFontSize(run.getFontSize());
        textRun.setColor("#" + run.getColor());
        
        // ★ 处理内嵌图片
        List<XWPFPicture> pictures = run.getEmbeddedPictures();
        if (!pictures.isEmpty()) {
            List<EmbeddedImage> embeddedImages = new ArrayList<>();
            for (XWPFPicture pic : pictures) {
                XWPFPictureData pictureData = pic.getPictureData();
                
                // 上传图片
                MultipartFile imageFile = new MockMultipartFile(
                    pictureData.getFileName(),
                    pictureData.getFileName(),
                    "image/jpeg",
                    new ByteArrayInputStream(pictureData.getData())
                );
                AttachDto uploadResult = documentAttachService.upload(imageFile, docId, 0);
                
                // 保存上传后的URL
                EmbeddedImage img = new EmbeddedImage();
                img.setSrc(uploadResult.getFilePath());  // ★ 关键：上传后的URL
                img.setWidth(pic.getWidth());
                img.setHeight(pic.getHeight());
                embeddedImages.add(img);
            }
            textRun.setEmbeddedImages(embeddedImages);
        }
        
        // ★ 处理OLE附件（类似逻辑）
        // ...
        
        return textRun;
    }
    
    private String getFileNameWithoutExtension(String filename) {
        int dotIndex = filename.lastIndexOf(".");
        return dotIndex > 0 ? filename.substring(0, dotIndex) : filename;
    }
}
```

---

## 相关文档

- **LEGACY_MAPPING.md** - 传统代码详细映射
- **ENHANCEMENTS.md** - 增强功能说明
- **INTEGRATION.md** - 集成指南
- **QUICK_REFERENCE.md** - 快速参考

如有其他问题，请参考上述文档或提Issue讨论。
