# 传统代码映射文档 (Legacy Code Mapping)

本文档说明了传统Word处理代码（基于Apache POI）与当前TipTap转换器之间的映射关系。

## 概述

传统代码位于：`DocWordServiceImpl.java`  
新转换器位于：`TiptapConverter.java`

## 核心方法映射

### 1. 文档导入流程 (Document Import Flow)

| 传统方法 | 新转换器方法 | 说明 |
|---------|-------------|------|
| `docImport()` | `convert()` | 主入口方法 |
| `processJsonObject()` | `convertBlock()` | 处理单个元素 |

**传统代码**：
```java
public void docImport(MultipartFile file, String docId) {
    XWPFDocument document = new XWPFDocument(fis);
    for (IBodyElement element : document.getBodyElements()) {
        if (element instanceof XWPFParagraph) {
            // 处理段落
            setParagraphStyle(paragraph, ...);
            paragraph.getRuns().forEach(run -> {
                setParagraphText(run, ...);
                setPictures(run, ...);
                setFile(run, ...);
            });
        } else if (element instanceof XWPFTable) {
            // 处理表格
            parseTable(table, ...);
        }
    }
}
```

**新转换器**：
```java
public Map<String, Object> convert(Object documentData) {
    List<?> blocks = getFieldValue(documentData, "getBlocks");
    List<Map<String, Object>> content = convertBlocks(blocks);
    // 通过反射自动处理所有block类型
}
```

### 2. 段落样式检测 (Paragraph Style Detection)

| 传统方法 | 新转换器方法 | 说明 |
|---------|-------------|------|
| `setParagraphStyle()` | `detectHeadingFromStyle()` | 检测标题级别 |
| `WordUtil.getHeadingLv()` | `detectHeadingFromStyle()` | 从样式名提取级别 |

**传统代码**：
```java
private void setParagraphStyle(XWPFParagraph paragraph, ...) {
    int level = WordUtil.getHeadingLv(doc.getStyles(), paragraph.getStyleID());
    if (level > 0) {
        paragraphNode.put("type", "heading");
        paraAttrNode.put("level", level);
    } else {
        paragraphNode.put("type", "paragraph");
    }
}
```

**新转换器**：
```java
private Map<String, Object> convertParagraphBlock(Object block) {
    String paragraphStyle = getFieldValue(block, "getStyle");
    Integer styleLevel = detectHeadingFromStyle(paragraphStyle);
    if (styleLevel != null && styleLevel > 0) {
        node.put("type", "heading");
        // 创建heading节点
    } else {
        node.put("type", "paragraph");
        // 创建paragraph节点
    }
}

private Integer detectHeadingFromStyle(String styleName) {
    // 支持 "Heading 1", "Heading1", "标题 1" 等格式
    // 返回标题级别 1-6
}
```

### 3. 文本样式处理 (Text Style Processing)

| 传统方法 | 新转换器方法 | 说明 |
|---------|-------------|------|
| `setParagraphText()` | `createTextNode()` | 创建文本节点 |
| `run.isBold()` | `getFieldValue(run, "getBold")` | 粗体检测 |
| `run.isItalic()` | `getFieldValue(run, "getItalic")` | 斜体检测 |
| `run.getColor()` | `getFieldValue(run, "getColor")` | 文字颜色 |
| `run.getTextHighlightColor()` | `getFieldValue(run, "getHighlight")` | 高亮颜色 |
| `run.getFontFamily()` | `getFieldValue(run, "getFontFamily")` | 字体 |
| `run.getFontSize()` | `getFieldValue(run, "getFontSize")` | 字号 |

**传统代码**：
```java
private void setParagraphText(XWPFRun run, ...) {
    if (run.isBold()) {
        ObjectNode typeNode = mapper.createObjectNode();
        typeNode.put("type", "bold");
        markContent.add(typeNode);
    }
    if (run.isItalic()) {
        // 添加italic标记
    }
    String color = run.getColor();
    if (null != color) {
        attrNode.put("color", color);
    }
    // ... 更多样式处理
}
```

**新转换器**：
```java
private Map<String, Object> createTextNode(String text, Object run) {
    List<Map<String, Object>> marks = new ArrayList<>();
    
    Boolean bold = getFieldValue(run, "getBold");
    if (Boolean.TRUE.equals(bold)) {
        marks.add(Map.of("type", "bold"));
    }
    
    Boolean italic = getFieldValue(run, "getItalic");
    if (Boolean.TRUE.equals(italic)) {
        marks.add(Map.of("type", "italic"));
    }
    
    // 🆕 新增：删除线支持
    Boolean strikethrough = getFieldValue(run, "getStrikethrough");
    if (Boolean.TRUE.equals(strikethrough)) {
        marks.add(Map.of("type", "strike"));
    }
    
    // 🆕 新增：上下标支持
    String verticalAlign = getFieldValue(run, "getVerticalAlign");
    if ("subscript".equalsIgnoreCase(verticalAlign)) {
        marks.add(Map.of("type", "subscript"));
    }
    
    // ... 更多标记
}
```

### 4. 图片处理 (Image Processing)

| 传统方法 | 新转换器方法 | 说明 |
|---------|-------------|------|
| `setPictures()` | `convertEmbeddedImage()` | 处理内嵌图片 |
| `run.getEmbeddedPictures()` | `getFieldValue(run, "getEmbeddedImages")` | 获取图片列表 |

**传统代码**：
```java
private void setPictures(XWPFRun run, ArrayNode paragraphContent, ...) {
    List<XWPFPicture> pictures = run.getEmbeddedPictures();
    if (CollectionUtil.isNotEmpty(pictures)) {
        for (XWPFPicture xwpfPicture : pictures) {
            XWPFPictureData picture = xwpfPicture.getPictureData();
            // 上传图片
            AttachDto upload = documentAttachService.upload(...);
            
            ObjectNode imageNode = mapper.createObjectNode();
            imageNode.put("type", "image");
            ObjectNode imageAttrNode = mapper.createObjectNode();
            imageAttrNode.put("src", upload.getFilePath());
            imageNode.set("attrs", imageAttrNode);
            paragraphContent.add(imageNode);
        }
    }
}
```

**新转换器**：
```java
private List<Map<String, Object>> convertRuns(List<?> runs) {
    for (Object run : runs) {
        // 检查内嵌图片
        List<?> embeddedImages = getFieldValue(run, "getEmbeddedImages");
        if (embeddedImages != null && !embeddedImages.isEmpty()) {
            for (Object img : embeddedImages) {
                content.add(convertEmbeddedImage(img));
            }
        }
        // ... 处理文本
    }
}

private Map<String, Object> convertEmbeddedImage(Object imageData) {
    Map<String, Object> node = new LinkedHashMap<>();
    node.put("type", "image");
    
    Map<String, Object> attrs = new LinkedHashMap<>();
    String src = getFieldValue(imageData, "getSrc");
    // 支持多种字段名：src, url, path
    attrs.put("src", src);
    // ... 其他属性
}
```

### 5. 附件处理 (Attachment Processing)

| 传统方法 | 新转换器方法 | 说明 |
|---------|-------------|------|
| `setFile()` | `convertEmbeddedAttachment()` | 处理OLE附件 |
| `setAttachment()` | `convertUploadFileBlock()` | 处理上传文件 |

**传统代码 - setFile()**：
```java
private void setFile(XWPFRun run, ArrayNode paragraphContent, ...) {
    CTR ctr = run.getCTR();
    XmlObject[] oleObjects = ctr.selectPath(...);
    for (XmlObject oleObject : oleObjects) {
        // 提取OLE对象
        POIXMLDocumentPart documentPart = run.getDocument().getRelationById(rId);
        byte[] data = IOUtils.toByteArray(olePart.getInputStream());
        
        AttachDto attachDto = docxImportService.covert(data, ...);
        
        ObjectNode fileNode = mapper.createObjectNode();
        fileNode.put("type", "uploadFileNode");
        ObjectNode fileAttrNode = mapper.createObjectNode();
        fileAttrNode.put("src", attachDto.getFilePath());
        fileAttrNode.put("filename", fileName);
        fileAttrNode.put("filetype", "...");
        fileAttrNode.put("filesize", attachDto.getFileSize());
        fileNode.set("attrs", fileAttrNode);
    }
}
```

**传统代码 - setAttachment()**：
```java
private void setAttachment(JsonNode jsonObject, XWPFDocument doc, ...) {
    String fileSrc = paragraphContent.get("src").asText();
    String fileName = paragraphContent.get("filename").asText();
    
    // 定义附件占位符
    String attachmentPlaceholder = "{{" + fileId + "}}";
    XWPFRun run = paragraph.createRun();
    run.setText(attachmentPlaceholder);
}
```

**新转换器**：
```java
// 在convertRuns中处理
private List<Map<String, Object>> convertRuns(List<?> runs) {
    // 检查内嵌附件
    List<?> embeddedAttachments = getFieldValue(run, "getEmbeddedAttachments");
    if (embeddedAttachments != null && !embeddedAttachments.isEmpty()) {
        for (Object attachment : embeddedAttachments) {
            content.add(convertEmbeddedAttachment(attachment));
        }
    }
}

// 新增：uploadFileNode支持
private Map<String, Object> convertUploadFileBlock(Object block) {
    Map<String, Object> node = new LinkedHashMap<>();
    node.put("type", "uploadFileNode");
    
    Map<String, Object> attrs = new LinkedHashMap<>();
    // 支持多种字段名：src, url, path
    String src = getFieldValue(block, "getSrc");
    if (src == null) src = getFieldValue(block, "getUrl");
    if (src == null) src = getFieldValue(block, "getPath");
    
    // 支持多种文件名字段：filename, fileName, name
    String filename = getFieldValue(block, "getFilename");
    if (filename == null) filename = getFieldValue(block, "getFileName");
    if (filename == null) filename = getFieldValue(block, "getName");
    
    attrs.put("src", src);
    attrs.put("path", src);
    attrs.put("filename", filename);
    attrs.put("filetype", getFieldValue(block, "getFiletype"));
    attrs.put("filesize", getFieldValue(block, "getFilesize"));
    
    node.put("attrs", attrs);
    return node;
}
```

### 6. 表格处理 (Table Processing)

| 传统方法 | 新转换器方法 | 说明 |
|---------|-------------|------|
| `parseTable()` | `convertTableBlock()` | 解析表格 |

**传统代码**：
```java
private static ObjectNode parseTable(XWPFTable table, ObjectMapper objectMapper) {
    ObjectNode tableNode = objectMapper.createObjectNode();
    tableNode.put("type", "table");
    ArrayNode tableContent = objectMapper.createArrayNode();
    
    List<XWPFTableRow> rows = table.getRows();
    for (XWPFTableRow row : rows) {
        ObjectNode rowNode = objectMapper.createObjectNode();
        rowNode.put("type", "tableRow");
        ArrayNode rowContent = objectMapper.createArrayNode();
        
        for (XWPFTableCell cell : row.getTableCells()) {
            ObjectNode cellNode = objectMapper.createObjectNode();
            cellNode.put("type", "tableCell");
            cellNode.put("text", cell.getText());
            // ...
        }
    }
}
```

**新转换器（增强版）**：
```java
private Map<String, Object> convertTableBlock(Object block) {
    // 支持表格级属性
    Integer tableWidth = getFieldValue(block, "getWidth");
    String tableBorder = getFieldValue(block, "getBorder");
    
    for (Object rowObj : rows) {
        // 🆕 支持行高
        Integer rowHeight = getFieldValue(rowObj, "getHeight");
        
        for (Object cell : row) {
            // 🆕 支持单元格属性
            Integer colspan = getFieldValue(cell, "getColspan");
            Integer rowspan = getFieldValue(cell, "getRowspan");
            String cellBackground = getFieldValue(cell, "getBackgroundColor");
            
            // 🆕 支持富文本单元格
            List<?> cellRuns = getFieldValue(cell, "getRuns");
            if (cellRuns != null && !cellRuns.isEmpty()) {
                paraContent = convertRuns(cellRuns);
            }
        }
    }
}
```

## Block类型映射

| TipTap JSON Type | 传统代码处理 | 新转换器Block类型 |
|-----------------|-------------|------------------|
| `doc` | 根节点 | DocumentData |
| `heading` | setParagraphStyle检测 | HeadingBlock |
| `paragraph` | setParagraphText | ParagraphBlock |
| `text` | XWPFRun处理 | TextRun |
| `table` | parseTable | TableBlock |
| `tableRow` | XWPFTableRow | TableBlock.rows |
| `tableCell` | XWPFTableCell | TableCellData |
| `image` | setPictures | ImageBlock / EmbeddedImage |
| `uploadFileNode` | setFile | 🆕 UploadFileBlock |
| `attachment` | setAttachment | AttachmentBlock |
| `orderedList` | addOrderedList | ParagraphBlock (isListItem=true) |
| `bulletList` | addOrderedList | ParagraphBlock (isListItem=true) |

## 字段名兼容性

新转换器通过反射支持多种字段名，提供最大兼容性：

| 用途 | 传统字段 | 新转换器尝试的字段 |
|------|---------|------------------|
| 图片路径 | picture.getSrc() | getSrc() → getUrl() → getPath() |
| 文件名 | picture.getFileName() | getFilename() → getFileName() → getName() |
| 文件类型 | - | getFiletype() → getMimeType() → getContentType() |
| 文件大小 | attachDto.getFileSize() | getFilesize() → getSize() |

## 增强功能对比

| 功能 | 传统代码 | 新转换器 | 备注 |
|------|---------|---------|------|
| 基础格式 | ✅ bold, italic | ✅ bold, italic, underline | 一致 |
| 删除线 | ❌ | ✅ strikethrough | 新增 |
| 上下标 | ❌ | ✅ subscript/superscript | 新增 |
| 代码样式 | ❌ | ✅ code | 新增 |
| 高亮 | ✅ textHighlightColor | ✅ highlight | 增强 |
| 超链接 | ❌ | ✅ link | 新增 |
| 段落缩进 | ❌ | ✅ indentLeft/Right/FirstLine | 新增 |
| 段落间距 | ❌ | ✅ spacingBefore/After | 新增 |
| 段落边框 | ❌ | ✅ borderTop/Bottom/Left/Right | 新增 |
| 表格合并 | ❌ | ✅ colspan/rowspan | 新增 |
| 单元格背景 | ❌ | ✅ background | 新增 |
| 单元格边框 | ❌ | ✅ border* | 新增 |
| 富文本单元格 | ❌ 仅纯文本 | ✅ 支持runs | 增强 |

## 使用示例对比

### 传统代码使用方式

```java
@Autowired
private DocWordService docWordService;

// 导入
public void importDoc(MultipartFile file, String docId) {
    docWordService.docImport(file, docId);
}

// 导出
public void exportDoc(String docId, HttpServletResponse response) {
    docWordService.docExport(docId, response);
}
```

### 新转换器使用方式

```java
// 1. 从Word文件解析得到DocumentData
DocumentData doc = wordParser.parse(wordFile);

// 2. 转换为TipTap JSON
TiptapConverter converter = new TiptapConverter();
Map<String, Object> tiptapJson = converter.convert(doc);

// 3. 保存或使用
String json = new ObjectMapper().writeValueAsString(tiptapJson);
documentService.saveDocument(json);
```

## 关键差异

### 1. 架构差异

**传统代码**：
- 直接使用Apache POI API (XWPFDocument, XWPFParagraph, XWPFRun)
- 紧耦合于POI类型
- 在导入/导出方法中直接处理

**新转换器**：
- 使用反射处理任意对象
- 解耦于具体实现
- 只负责数据转换，不涉及文件I/O

### 2. 扩展性

**传统代码**：
- 新增字段需要修改多处代码
- 硬编码字段访问
- 难以支持新的Block类型

**新转换器**：
- 新增字段自动支持（如果模型类有getter）
- 通过反射动态访问
- 轻松支持新Block类型（添加case分支）

### 3. 可维护性

**传统代码**：
- 业务逻辑（上传、保存）与转换逻辑混合
- 方法较长，职责不清
- 依赖外部服务（MinIO、数据库）

**新转换器**：
- 单一职责：仅做数据转换
- 方法职责明确
- 无外部依赖，易于测试

## 迁移建议

如果要从传统代码迁移到新转换器：

1. **保留Word解析部分**：使用Apache POI解析Word → DocumentData
2. **使用新转换器**：DocumentData → TipTap JSON
3. **保留业务逻辑**：文件上传、保存等业务逻辑独立处理

**迁移步骤**：
```java
// Step 1: 解析Word文件（保留或改进）
public DocumentData parseWordFile(InputStream inputStream) {
    XWPFDocument xwpfDoc = new XWPFDocument(inputStream);
    DocumentData documentData = new DocumentData();
    
    for (IBodyElement element : xwpfDoc.getBodyElements()) {
        if (element instanceof XWPFParagraph) {
            ParagraphBlock block = parseParagraph((XWPFParagraph) element);
            documentData.getBlocks().add(block);
        }
        // ... 其他元素
    }
    
    return documentData;
}

// Step 2: 使用新转换器
public String convertToTipTap(DocumentData documentData) {
    TiptapConverter converter = new TiptapConverter();
    Map<String, Object> tiptapJson = converter.convert(documentData);
    return new ObjectMapper().writeValueAsString(tiptapJson);
}

// Step 3: 整合
public void docImport(MultipartFile file, String docId) {
    DocumentData documentData = parseWordFile(file.getInputStream());
    String tiptapJson = convertToTipTap(documentData);
    documentService.saveDocument(docId, tiptapJson);
}
```

## 总结

新转换器完全覆盖了传统代码的核心转换逻辑，并提供了以下优势：

1. ✅ **功能完整** - 支持所有传统功能 + 更多增强
2. ✅ **更好的架构** - 解耦、单一职责、易测试
3. ✅ **更强的扩展性** - 反射机制、动态字段访问
4. ✅ **无损转换** - 所有数据完整保留
5. ✅ **向后兼容** - 字段名多种尝试机制

通过本文档，您可以清楚地了解传统代码与新转换器之间的对应关系，便于理解、维护和迁移。
