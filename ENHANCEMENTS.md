# 增强功能说明 (Enhancement Guide)

## 新增功能 (New Features)

基于传统Word文档导入逻辑，转换器已增强以下功能：

### 1. 智能标题检测 (Smart Heading Detection)

**功能**：自动识别Word段落样式并转换为标题

**支持的样式模式**：
- `Heading 1-6` / `Heading1-6`
- `标题 1-6` / `标题1-6`
- `Title` → Heading 1
- `Subtitle` / `副标题` → Heading 2

**实现方法**：`detectHeadingFromStyle()`

**示例**：
```java
// 输入ParagraphBlock
{
  style: "Heading 2",
  runs: [...]
}

// 输出TipTap节点
{
  "type": "heading",
  "attrs": {
    "level": 2,
    "originLevel": 2,
    "styleSource": "Heading 2"
  },
  "content": [...]
}
```

### 2. 增强段落格式 (Enhanced Paragraph Formatting)

**新增支持的属性**：

#### 缩进 (Indentation)
- `indentLeft` - 左缩进
- `indentRight` - 右缩进
- `indentFirstLine` - 首行缩进

#### 间距 (Spacing)
- `spacingBefore` - 段前间距
- `spacingAfter` - 段后间距
- `lineSpacing` - 行距

#### 边框 (Borders)
- `borderTop` - 上边框
- `borderBottom` - 下边框
- `borderLeft` - 左边框
- `borderRight` - 右边框

#### 背景色 (Background)
- `backgroundColor` - 段落背景色

**实现方法**：`addParagraphFormatting()`

**示例**：
```json
{
  "type": "paragraph",
  "attrs": {
    "textAlign": "justify",
    "indentLeft": 720,
    "indentFirstLine": 360,
    "spacingBefore": 100,
    "spacingAfter": 100,
    "lineSpacing": 1.5,
    "backgroundColor": "#F0F0F0",
    "origin": {...}
  },
  "content": [...]
}
```

### 3. 增强文本标记 (Enhanced Text Marks)

**新增支持的标记类型**：

#### 基础格式
- ✅ `bold` - 粗体
- ✅ `italic` - 斜体
- ✅ `underline` - 下划线
- 🆕 `strike` - 删除线 (strikethrough)
- 🆕 `code` - 代码/等宽字体

#### 上下标
- 🆕 `subscript` - 下标
- 🆕 `superscript` - 上标

#### 高亮
- 🆕 `highlight` - 高亮标记
  - `attrs.color` - 高亮颜色

#### 超链接
- 🆕 `link` - 超链接
  - `attrs.href` - 链接URL
  - `attrs.linkId` - 链接ID
  - `attrs.target` - 打开方式

**实现方法**：增强的 `createTextNode()`

**示例**：
```json
{
  "type": "text",
  "text": "Enhanced text",
  "marks": [
    {"type": "bold"},
    {"type": "strike"},
    {
      "type": "highlight",
      "attrs": {"color": "#FFFF00"}
    },
    {
      "type": "link",
      "attrs": {
        "href": "https://example.com",
        "target": "_blank"
      }
    }
  ]
}
```

### 4. 增强表格支持 (Enhanced Table Support)

**表格级属性** (Table Level):
- `tableWidth` - 表格宽度
- `tableBorder` - 表格边框样式
- `tableAlignment` - 表格对齐方式

**行级属性** (Row Level):
- `height` - 行高

**单元格属性** (Cell Level):
- `colspan` - 列合并数
- `rowspan` - 行合并数
- `colwidth` - 列宽度数组
- `background` - 单元格背景色
- `verticalAlign` - 垂直对齐方式
- `borderTop/Bottom/Left/Right` - 单元格边框

**富文本单元格**：
- 支持单元格内的格式化文本（runs）
- 支持单元格内的多种样式

**实现方法**：增强的 `convertTableBlock()`

**示例**：
```json
{
  "type": "table",
  "attrs": {
    "tableWidth": 5000,
    "tableBorder": "single",
    "origin": {...}
  },
  "content": [
    {
      "type": "tableRow",
      "attrs": {"height": 500},
      "content": [
        {
          "type": "tableCell",
          "attrs": {
            "colspan": 2,
            "background": "#E0E0E0",
            "verticalAlign": "middle"
          },
          "content": [
            {
              "type": "paragraph",
              "content": [
                {
                  "type": "text",
                  "text": "Header Cell",
                  "marks": [{"type": "bold"}]
                }
              ]
            }
          ]
        }
      ]
    }
  ]
}
```

### 5. 内嵌内容支持 (Embedded Content Support)

**功能**：支持在文本运行（TextRun）中嵌入图片和附件

这模拟了传统Word文档中的行内图片和附件功能。

#### 内嵌图片 (Embedded Images)
从 `TextRun.embeddedImages` 提取：
- 支持多种路径字段：`src`, `url`, `path`
- 保留尺寸信息：`width`, `height`
- 保留描述信息：`alt`, `title`

#### 内嵌附件 (Embedded Attachments)
从 `TextRun.embeddedAttachments` 提取：
- 支持多种名称字段：`name`, `fileName`
- 支持多种路径字段：`url`, `path`
- 保留文件信息：`size`, `mimeType`

**实现方法**：
- `convertRuns()` - 增强以处理嵌入内容
- `convertEmbeddedImage()` - 转换内嵌图片
- `convertEmbeddedAttachment()` - 转换内嵌附件

**示例**：
```java
// 输入TextRun
{
  text: "Before image ",
  embeddedImages: [
    {src: "image1.png", width: 300, height: 200}
  ],
  text: " after image"
}

// 输出内容节点
[
  {"type": "text", "text": "Before image "},
  {
    "type": "image",
    "attrs": {
      "src": "image1.png",
      "width": 300,
      "height": 200,
      "origin": {...}
    }
  },
  {"type": "text", "text": " after image"}
]
```

## 字段映射参考 (Field Mapping Reference)

### ParagraphBlock 字段
| 字段名 | TipTap位置 | 说明 |
|--------|-----------|------|
| `style` | `attrs.styleSource` | 段落样式名 |
| `alignment` | `attrs.textAlign` | 文本对齐 |
| `indentLeft` | `attrs.indentLeft` | 左缩进 |
| `indentRight` | `attrs.indentRight` | 右缩进 |
| `indentFirstLine` | `attrs.indentFirstLine` | 首行缩进 |
| `spacingBefore` | `attrs.spacingBefore` | 段前间距 |
| `spacingAfter` | `attrs.spacingAfter` | 段后间距 |
| `lineSpacing` | `attrs.lineSpacing` | 行距 |
| `backgroundColor` | `attrs.backgroundColor` | 背景色 |
| `borderTop/Bottom/Left/Right` | `attrs.border*` | 边框 |

### TextRun 字段
| 字段名 | TipTap Mark | 说明 |
|--------|-------------|------|
| `bold` | `bold` | 粗体 |
| `italic` | `italic` | 斜体 |
| `underline` | `underline` | 下划线 |
| `strikethrough` | `strike` | 删除线 |
| `code` | `code` | 代码样式 |
| `verticalAlign: "subscript"` | `subscript` | 下标 |
| `verticalAlign: "superscript"` | `superscript` | 上标 |
| `highlight` | `highlight` | 高亮 |
| `hyperlinkUrl` | `link` | 超链接 |
| `embeddedImages` | `image` 节点 | 内嵌图片 |
| `embeddedAttachments` | `attachment` 节点 | 内嵌附件 |

### TableCellData 字段
| 字段名 | TipTap位置 | 说明 |
|--------|-----------|------|
| `colspan` | `attrs.colspan` | 列合并 |
| `rowspan` | `attrs.rowspan` | 行合并 |
| `width` | `attrs.colwidth` | 列宽 |
| `backgroundColor` | `attrs.background` | 背景色 |
| `verticalAlign` | `attrs.verticalAlign` | 垂直对齐 |
| `borderTop/Bottom/Left/Right` | `attrs.border*` | 单元格边框 |
| `runs` | `content[0].content` | 富文本内容 |

## 兼容性说明 (Compatibility Notes)

### 向后兼容
所有增强功能都是**可选的**，通过反射动态检测字段：
- 如果字段不存在，不会报错，只是不添加该属性
- 保持与旧版本model的兼容性
- 核心转换逻辑不受影响

### 字段检测顺序
多个可能的字段名会按顺序尝试：
```java
// 示例：图片路径检测
src = getFieldValue(imageData, "getSrc");       // 优先
if (src == null) 
    src = getFieldValue(imageData, "getUrl");   // 其次
if (src == null) 
    src = getFieldValue(imageData, "getPath");  // 最后
```

## 使用建议 (Usage Recommendations)

### 1. 模型类应包含的字段

为了充分利用增强功能，建议DocumentData模型包含：

**ParagraphBlock**：
```java
public class ParagraphBlock extends ContentBlock {
    private String style;              // 段落样式名
    private Alignment alignment;
    private Integer indentLeft;
    private Integer indentRight;
    private Integer indentFirstLine;
    private Integer spacingBefore;
    private Integer spacingAfter;
    private Double lineSpacing;
    private String backgroundColor;
    private Border borderTop;
    // ... 其他字段
}
```

**TextRun**：
```java
public class TextRun {
    private String text;
    private Boolean bold;
    private Boolean italic;
    private Boolean underline;
    private Boolean strikethrough;     // 新增
    private Boolean code;              // 新增
    private String verticalAlign;      // 新增："subscript" / "superscript"
    private String highlight;          // 新增
    private String hyperlinkUrl;       // 新增
    private String hyperlinkId;        // 新增
    private List<EmbeddedImage> embeddedImages;        // 新增
    private List<EmbeddedAttachment> embeddedAttachments; // 新增
    // ... 其他字段
}
```

**TableCellData**：
```java
public class TableCellData {
    private String text;
    private List<TextRun> runs;        // 新增：支持富文本
    private Integer colspan;
    private Integer rowspan;
    private Integer width;
    private String backgroundColor;
    private String verticalAlign;
    private Border borderTop;
    // ... 其他字段
}
```

### 2. 性能考虑

- 反射调用有轻微性能开销，但对于文档转换场景可忽略
- 大型表格建议分批处理
- 内嵌图片较多时注意内存使用

### 3. 自定义扩展

如需添加更多字段支持，只需：
1. 在模型类中添加getter方法
2. 转换器会自动通过反射获取
3. 不需要修改转换器代码

## 示例：完整转换流程

```java
// 1. 创建DocumentData（假设从Word解析得到）
DocumentData doc = wordParser.parse(wordFile);

// 2. 转换为TipTap JSON
TiptapConverter converter = new TiptapConverter();
Map<String, Object> tiptapDoc = converter.convert(doc);

// 3. 序列化为JSON字符串
ObjectMapper mapper = new ObjectMapper();
String json = mapper.writerWithDefaultPrettyPrinter()
                   .writeValueAsString(tiptapDoc);

// 4. 保存或传输
saveToDatabase(json);
```

## 总结 (Summary)

增强后的转换器：
- ✅ 保持原有的无损转换特性
- ✅ 支持更丰富的Word文档特性
- ✅ 模拟传统POI处理逻辑
- ✅ 向后兼容
- ✅ 可扩展性强

所有增强功能都通过反射实现，不破坏原有架构！
