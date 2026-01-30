# 集成指南 (Integration Guide)

## 如何在您的项目中使用TipTap转换器

本转换器设计为与包含DocumentData和Block类的外部JAR配合使用。

### 方式一：作为独立项目构建

#### 1. 添加您的模型JAR依赖

编辑 `pom.xml`，添加您的文档模型依赖：

```xml
<dependencies>
    <!-- 您的文档模型JAR -->
    <dependency>
        <groupId>com.yourcompany</groupId>
        <artifactId>document-model</artifactId>
        <version>1.0.0</version>
        <scope>provided</scope>
    </dependency>
    
    <!-- 保留现有依赖 -->
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
        <version>2.15.2</version>
    </dependency>
</dependencies>
```

#### 2. 构建转换器JAR

```bash
mvn clean package
```

生成的JAR位于：`target/tiptap-converter-1.0.0.jar`

#### 3. 在您的应用中使用

```bash
# 在classpath中包含两个JAR
java -cp your-document-model.jar:tiptap-converter-1.0.0.jar:jackson-databind-2.15.2.jar \
     your.main.Application
```

```java
// 在您的代码中
import com.tiptap.converter.TiptapConverter;
import com.yourcompany.model.DocumentData;

public class YourApplication {
    public static void main(String[] args) {
        // 创建或加载DocumentData
        DocumentData doc = loadDocument();
        
        // 转换
        TiptapConverter converter = new TiptapConverter();
        Map<String, Object> tiptapJson = converter.convert(doc);
        
        // 使用结果
        processResult(tiptapJson);
    }
}
```

### 方式二：作为源码集成

#### 1. 复制源码到您的项目

```bash
# 复制转换器类到您的项目
cp src/main/java/com/tiptap/converter/TiptapConverter.java \
   your-project/src/main/java/com/yourpackage/converter/
```

#### 2. 调整package名称

编辑 `TiptapConverter.java` 第一行：

```java
package com.yourpackage.converter;  // 改为您的包名
```

#### 3. 确保Jackson依赖存在

在您的 `pom.xml` 中：

```xml
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
    <version>2.15.2</version>
</dependency>
```

#### 4. 直接使用

```java
import com.yourpackage.converter.TiptapConverter;
import com.yourpackage.model.DocumentData;

TiptapConverter converter = new TiptapConverter();
Map<String, Object> result = converter.convert(documentData);
```

## 示例：完整使用流程

### 示例1：读取JSON并转换

```java
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiptap.converter.TiptapConverter;
import java.io.File;
import java.util.Map;

public class ConvertExample {
    public static void main(String[] args) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        
        // 1. 从JSON文件读取DocumentData
        // 注意：需要配置Jackson识别您的模型类
        DocumentData doc = mapper.readValue(
            new File("examples/source.txt"), 
            DocumentData.class
        );
        
        // 2. 转换
        TiptapConverter converter = new TiptapConverter();
        Map<String, Object> tiptapDoc = converter.convert(doc);
        
        // 3. 输出为JSON
        String json = mapper.writerWithDefaultPrettyPrinter()
                           .writeValueAsString(tiptapDoc);
        System.out.println(json);
        
        // 4. 或者写入文件
        mapper.writerWithDefaultPrettyPrinter()
              .writeValue(new File("output.json"), tiptapDoc);
    }
}
```

### 示例2：程序化创建并转换

```java
import com.tiptap.converter.TiptapConverter;
import com.yourcompany.model.*;
import java.util.Map;

public class ProgrammaticExample {
    public static void main(String[] args) {
        // 1. 创建DocumentData
        DocumentData doc = new DocumentData();
        doc.setTitle("My Document");
        doc.setSourceName("example.docx");
        
        // 2. 添加标题
        HeadingBlock heading = new HeadingBlock();
        heading.setLevel(2);
        heading.setText("Introduction");
        doc.getBlocks().add(heading);
        
        // 3. 添加段落
        ParagraphBlock para = new ParagraphBlock();
        para.setAlignment(Alignment.LEFT);
        
        TextRun run1 = new TextRun();
        run1.setText("This is ");
        para.getRuns().add(run1);
        
        TextRun run2 = new TextRun();
        run2.setText("bold");
        run2.setBold(true);
        para.getRuns().add(run2);
        
        TextRun run3 = new TextRun();
        run3.setText(" text.");
        para.getRuns().add(run3);
        
        doc.getBlocks().add(para);
        
        // 4. 转换
        TiptapConverter converter = new TiptapConverter();
        Map<String, Object> result = converter.convert(doc);
        
        // 5. 使用结果
        System.out.println("Converted successfully!");
        System.out.println("Document type: " + result.get("type"));
        System.out.println("Content blocks: " + 
            ((java.util.List<?>) result.get("content")).size());
    }
}
```

### 示例3：批量转换

```java
import com.tiptap.converter.TiptapConverter;
import java.util.*;
import java.io.File;

public class BatchConverter {
    public static void main(String[] args) {
        TiptapConverter converter = new TiptapConverter();
        
        // 批量转换多个文档
        List<DocumentData> documents = loadDocuments();
        List<Map<String, Object>> results = new ArrayList<>();
        
        for (DocumentData doc : documents) {
            try {
                Map<String, Object> tiptapDoc = converter.convert(doc);
                results.add(tiptapDoc);
                System.out.println("✓ Converted: " + doc.getTitle());
            } catch (Exception e) {
                System.err.println("✗ Failed: " + doc.getTitle());
                e.printStackTrace();
            }
        }
        
        System.out.println("Converted " + results.size() + 
                         " out of " + documents.size() + " documents");
    }
    
    private static List<DocumentData> loadDocuments() {
        // 实现您的文档加载逻辑
        return new ArrayList<>();
    }
}
```

## 自定义转换逻辑

如果您需要自定义某些Block的转换方式，可以继承`TiptapConverter`：

```java
package com.yourcompany.converter;

import com.tiptap.converter.TiptapConverter;
import java.util.*;

public class CustomTiptapConverter extends TiptapConverter {
    
    @Override
    protected Map<String, Object> convertHeadingBlock(Object block) {
        // 自定义标题转换逻辑
        Map<String, Object> node = super.convertHeadingBlock(block);
        
        // 添加自定义属性
        @SuppressWarnings("unchecked")
        Map<String, Object> attrs = (Map<String, Object>) node.get("attrs");
        attrs.put("customField", "customValue");
        
        return node;
    }
    
    // 或添加新的Block类型支持
    private Map<String, Object> convertCustomBlock(Object block) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("type", "customNode");
        // ... 实现转换逻辑
        return node;
    }
}
```

## 故障排查

### 问题1：ClassNotFoundException

**症状**：`java.lang.ClassNotFoundException: com.yourcompany.model.DocumentData`

**解决**：确保您的模型JAR在classpath中
```bash
java -cp your-model.jar:tiptap-converter.jar:... YourApp
```

### 问题2：NoSuchMethodException

**症状**：转换时抛出 `NoSuchMethodException`

**原因**：模型类缺少getter方法

**解决**：确保您的模型类有标准的getter方法：
```java
public class YourBlock {
    private String field;
    
    // 必须有getter
    public String getField() {
        return field;
    }
}
```

### 问题3：转换结果不完整

**检查**：
1. 模型类的所有字段是否都有getter方法
2. getter方法命名是否符合JavaBean规范
3. 查看日志中是否有反射异常

**调试**：
```java
// 启用详细日志
Logger logger = LoggerFactory.getLogger(TiptapConverter.class);
logger.setLevel(Level.DEBUG);
```

## 性能优化建议

### 大文档处理

对于大型文档（1000+ blocks），建议：

1. **复用转换器实例**
```java
// 不要每次都new
TiptapConverter converter = new TiptapConverter();
for (DocumentData doc : docs) {
    converter.convert(doc);
}
```

2. **流式处理**
```java
// 处理后立即序列化，避免在内存中积累
ObjectMapper mapper = new ObjectMapper();
for (DocumentData doc : docs) {
    Map<String, Object> result = converter.convert(doc);
    mapper.writeValue(outputStream, result);
    result = null; // 帮助GC
}
```

3. **并行转换**（如果文档独立）
```java
List<DocumentData> docs = loadDocuments();
List<Map<String, Object>> results = docs.parallelStream()
    .map(doc -> converter.convert(doc))
    .collect(Collectors.toList());
```

## 许可证

MIT License - 您可以自由使用、修改和分发此代码。
