# TipTap Converter

通用Java转换器，将Word文档解析对象（DocumentData + *Block）**无损**转换为TipTap/ProseMirror JSON格式。

## 功能特性

✅ **无损转换** - 所有字段完整保留，不丢失任何信息  
✅ **类型安全** - 通过反射动态处理外部JAR中的模型类  
✅ **可扩展** - 支持未来新增的Block类型  
✅ **标准兼容** - 输出符合TipTap/ProseMirror规范  
🆕 **增强功能** - 支持样式检测、富文本表格、内嵌内容等高级特性

## 🆕 最新增强功能

基于传统Word文档处理逻辑，转换器已增强以下功能：

### 1. 智能标题检测
- 自动识别Word段落样式（Heading 1-6, 标题 1-6等）
- 支持Title、Subtitle等特殊样式
- 保留原始样式信息

### 2. 增强段落格式
- **缩进支持**：左缩进、右缩进、首行缩进
- **间距支持**：段前间距、段后间距、行距
- **边框支持**：上下左右四边边框
- **背景色**：段落背景颜色

### 3. 更多文本标记
- 🆕 删除线 (strikethrough)
- 🆕 上标/下标 (superscript/subscript)
- 🆕 代码样式 (code)
- 🆕 高亮标记 (highlight)
- 🆕 超链接 (link) - 支持文本内链接

### 4. 增强表格
- 单元格合并 (colspan/rowspan)
- 单元格背景色和边框
- 单元格垂直对齐
- 表格级属性（宽度、边框、对齐）
- 🆕 富文本单元格 - 支持单元格内格式化文本

### 5. 内嵌内容
- 🆕 支持TextRun中的内嵌图片
- 🆕 支持TextRun中的内嵌附件
- 模拟传统Word行内对象处理

详细功能说明请查看 [ENHANCEMENTS.md](ENHANCEMENTS.md)

## ❓ 常见问题

**Q1: 转换器如何识别标题？**  
支持两种方式：1) HeadingBlock类型直接识别；2) ParagraphBlock样式名自动检测（支持"Heading 1-6"、"标题 1-6"等）

**Q2: 图片和附件如何处理？会自动上传吗？**  
转换器**不负责上传**，只做数据转换。需要在构建DocumentData时预先上传并设置URL。

**详细解答**：
- [QUICK_ANSWERS.md](QUICK_ANSWERS.md) - 快速可视化回答（5分钟阅读）
- [FAQ.md](FAQ.md) - 完整详细解答（含代码示例）

## 快速开始

### 1. 添加依赖

在您的项目中添加文档模型JAR和本转换器：

```xml
<dependencies>
    <!-- 您的文档模型JAR -->
    <dependency>
        <groupId>your.group.id</groupId>
        <artifactId>your-document-model</artifactId>
        <version>x.x.x</version>
    </dependency>
    
    <!-- TipTap转换器 -->
    <dependency>
        <groupId>com.tiptap</groupId>
        <artifactId>tiptap-converter</artifactId>
        <version>1.0.0</version>
    </dependency>
</dependencies>
```

### 2. 使用转换器

```java
import com.tiptap.converter.TiptapConverter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

// 创建转换器实例
TiptapConverter converter = new TiptapConverter();

// 转换DocumentData到TipTap JSON
Map<String, Object> tiptapDoc = converter.convert(documentData);

// 输出JSON
ObjectMapper mapper = new ObjectMapper();
String json = mapper.writerWithDefaultPrettyPrinter()
                   .writeValueAsString(tiptapDoc);
System.out.println(json);
```

## 转换规则

### 文档结构

输入：`DocumentData` 对象  
输出：标准TipTap文档JSON

```json
{
  "type": "doc",
  "attrs": { /* 元信息 */ },
  "content": [ /* 内容节点 */ ]
}
```

### Block类型映射

| 输入Block | 输出节点 | 说明 |
|-----------|----------|------|
| `HeadingBlock` | `heading` | level映射：max(1, min(6, level-1)) |
| `ParagraphBlock` | `paragraph` | 普通段落 |
| `ParagraphBlock` (isListItem=true) | `orderedList`/`bulletList` | 自动合并连续列表项 |
| `TableBlock` | `table` | 完整表格结构 |
| `ImageBlock` | `image` | 图片节点 |
| `AttachmentBlock` | `attachment` | 附件节点 |

### HeadingBlock → heading

```java
// 输入
HeadingBlock {
  level: 2,
  text: "Introduction"
}

// 输出
{
  "type": "heading",
  "attrs": {
    "level": 1,           // 2 - 1 = 1
    "originLevel": 2      // 保留原始level
  },
  "content": [
    {"type": "text", "text": "Introduction"}
  ]
}
```

### ParagraphBlock → paragraph

```java
// 输入
ParagraphBlock {
  runs: [
    TextRun { text: "Normal ", bold: false },
    TextRun { text: "bold", bold: true, fontSize: 14 },
    TextRun { text: " text", bold: false }
  ],
  alignment: Alignment.LEFT
}

// 输出
{
  "type": "paragraph",
  "attrs": {
    "textAlign": "left",
    "origin": { /* 完整ParagraphBlock数据 */ }
  },
  "content": [
    {"type": "text", "text": "Normal "},
    {
      "type": "text",
      "text": "bold",
      "marks": [
        {"type": "bold"},
        {
          "type": "textStyle",
          "attrs": {
            "fontSize": "14",
            "originFontSize": 14
          }
        }
      ]
    },
    {"type": "text", "text": " text"}
  ]
}
```

### TextRun样式映射

| TextRun属性 | TipTap Mark |
|-------------|-------------|
| `bold: true` | `{"type": "bold"}` |
| `italic: true` | `{"type": "italic"}` |
| `underline: true` | `{"type": "underline"}` |
| `fontFamily` | `textStyle.attrs.fontFamily` |
| `fontSize` | `textStyle.attrs.fontSize` + `originFontSize` |
| `color` | `textStyle.attrs.color` |
| `backgroundColor` | `textStyle.attrs.backgroundColor` |

**重要规则：**
- ✅ 每个TextRun独立转换为一个text节点（**禁止合并**）
- ✅ 文本中的`\n`自动拆分为 text + hardBreak + text
- ✅ 所有样式属性完整保留

### 列表处理

连续的`ParagraphBlock`（`isListItem=true`，相同`listType`和`listLevel`）会自动合并为一个列表容器：

```java
// 输入
ParagraphBlock { isListItem: true, listType: "ordered", listLevel: 1, ... }
ParagraphBlock { isListItem: true, listType: "ordered", listLevel: 1, ... }

// 输出
{
  "type": "orderedList",
  "attrs": {
    "listLevel": 1,
    "numberingFormat": "decimal",
    "origin": { /* 第一个item的完整数据 */ }
  },
  "content": [
    {
      "type": "listItem",
      "content": [{"type": "paragraph", ...}]
    },
    {
      "type": "listItem",
      "content": [{"type": "paragraph", ...}]
    }
  ]
}
```

### TableBlock → table

```java
// 输入
TableBlock {
  rows: [
    [TableCellData("A1"), TableCellData("A2")],
    [TableCellData("B1"), TableCellData("B2")]
  ]
}

// 输出
{
  "type": "table",
  "attrs": {"origin": {...}},
  "content": [
    {
      "type": "tableRow",
      "content": [
        {
          "type": "tableCell",
          "content": [
            {
              "type": "paragraph",
              "content": [{"type": "text", "text": "A1"}]
            }
          ]
        },
        // ... 更多cells
      ]
    },
    // ... 更多rows
  ]
}
```

## 元信息保留

所有非content的字段都保存在 `doc.attrs` 中：

```json
{
  "type": "doc",
  "attrs": {
    "sourceName": "document.docx",
    "title": "Document Title",
    "properties": {...},
    "images": [...],
    "attachments": [...],
    "hyperlinks": [...],
    "comments": [...],
    "footnotes": [...],
    "mapped": {...}
  },
  "content": [...]
}
```

## 无损性保证

本转换器严格遵循以下原则：

1. ❌ **不删除任何字段** - 所有数据都进入JSON
2. ❌ **不合并TextRun** - 每个run独立保留
3. ❌ **不丢弃空值** - null/空数组也会保留
4. ✅ **TipTap无法表达的信息** - 进入`attrs.origin`
5. ✅ **保持原始顺序** - blocks顺序与输入一致
6. ✅ **支持扩展** - 未知Block类型自动包装

## 示例文件

项目提供了示例输入输出文件：

- `examples/source.txt` - DocumentData JSON示例
- `examples/target.txt` - 转换后的TipTap JSON示例

## 技术细节

### 反射机制

转换器使用Java反射动态访问外部JAR中的类，无需编译时依赖：

```java
private <T> T getFieldValue(Object obj, String methodName) {
    Method method = obj.getClass().getMethod(methodName);
    return (T) method.invoke(obj);
}
```

### 类型判断

通过类名判断Block类型：

```java
private String getBlockType(Object block) {
    return block.getClass().getSimpleName();
}
```

## 要求

- JDK 8+
- Jackson 2.15.2+
- 包含DocumentData和Block类的外部JAR

## 构建

```bash
mvn clean package
```

## 许可证

MIT License

---

## 常见问题

**Q: 转换是否真的无损？**  
A: 是的。所有字段都会保留在JSON中，要么在标准TipTap结构中，要么在`attrs.origin`中。

**Q: 如何处理未知的Block类型？**  
A: 未知类型会被包装为paragraph节点，完整数据保存在`attrs.origin`中。

**Q: 列表嵌套怎么办？**  
A: 通过`listLevel`字段识别嵌套层级，相同层级的连续项会合并。

**Q: 如何自定义转换规则？**  
A: 继承`TiptapConverter`类，重写相应的`convert*Block`方法。

