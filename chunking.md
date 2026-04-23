## ✅ Điểm mạnh của giải pháp:

1. **Header Propagation** - Giải quyết triệt để vấn đề cross-page
2. **parent_id** - Chìa khóa vàng để group context
3. **Table handling** - Giữ nguyên cấu trúc bảng là rất quan trọng
4. **Payload filtering** - Tận dụng tốt sức mạnh của Qdrant

## 🔧 Góp ý bổ sung:

### 1. **Bước 1 - Chunking:**
```java
// Nên thêm logic này:
- Overlap: 15-20% là đủ (20-30% hơi lãng phí token)
- Thêm metadata: "has_continuation": true/false 
  (để biết chunk sau có cùng section không)
```

### 2. **Bước 2 - Payload:**
Thêm field này vào JSON:
```json
{
  "chunk_order": 3,          // Thứ tự trong section
  "word_count": 250,         // Để estimate context length
  "is_header_propagated": true,  // Flag quan trọng!
  "table_metadata": {        // Tách riêng cho table
    "num_rows": 15,
    "num_cols": 4,
    "has_merged_cells": false
  }
}
```

### 3. **Bước 3 - Qdrant Config:**
```java
// Tạo payload index để filter nhanh:
qdrantClient.createPayloadIndex(collection, 
    "section_header", PayloadSchemaType.KEYWORD);
qdrantClient.createPayloadIndex(collection, 
    "parent_id", PayloadSchemaType.KEYWORD);
qdrantClient.createPayloadIndex(collection, 
    "chunk_type", PayloadSchemaType.KEYWORD);
```

### 4. **Bước 4 - Pipeline (QUAN TRỌNG NHẤT):**

**Vấn đề**: Bước 4 hiện tại mới chỉ lấy parent chunk, nhưng chưa giải quyết case:
> "Header ở trang 5, nội dung ở trang 6-7, user hỏi về section đó"

**Giải pháp bổ sung** - Thêm bước **Context Expansion**:

```java
// Sau khi rerank top-5:
List<Chunk> expandedContext = new ArrayList<>();

for (Chunk chunk : top5Results) {
    // 1. Lấy parent chunk (section header)
    Chunk parent = getParentChunk(chunk.getParentId());
    expandedContext.add(parent);
    
    // 2. Lấy TẤT CẢ sibling chunks cùng parent_id
    // Đây là bước QUAN TRỌNG để giải quyết cross-page
    List<Chunk> siblings = qdrantClient.search(
        filter("parent_id", chunk.getParentId())
              .and("doc_id", chunk.getDocId()),
        limit(10)  // Lấy đủ context của section
    );
    expandedContext.addAll(siblings);
    
    // 3. Sort by chunk_order/page_range
    expandedContext.sort(Comparator.comparing(Chunk::getChunkOrder));
}

// 4. Truncate thông minh (ưu tiên chunk có score cao)
String finalContext = truncateToContextWindow(expandedContext, maxTokens);
```

### 5. **Thêm Bước 5 - Prompt Engineering:**

```java
// Cấu trúc prompt rõ ràng:
String prompt = """
    Bạn là trợ lý AI. Dưới đây là thông tin từ tài liệu:
    
    [SECTION: {section_header}]
    [PAGES: {page_range}]
    
    {content_with_tables}
    
    ---
    
    Câu hỏi: {user_query}
    
    Hướng dẫn:
    - Nếu thông tin nằm ở nhiều trang, hãy tổng hợp đầy đủ
    - Với bảng, đọc kỹ tất cả các dòng
    - Trích dẫn số trang nếu có thể
    """;
```

## 📊 Kiến trúc tối ưu cuối cùng:

```
User Query
    ↓
Embedding + Qdrant Search (top-k=20)
    ↓
Rerank → top-5
    ↓
[EXPANSION] ← Bước MỚI quan trọng
    ├─ Get parent chunk (header)
    ├─ Get all siblings (cùng section)
    └─ Sort by page/chunk_order
    ↓
Truncate + Assembly
    ↓
LLM → Response
```

## ⚡ Tips performance:

1. **Cache parent chunks** trong Redis/MySQL (vì 1 section chỉ có 1 header)
2. **Batch query** siblings thay vì query từng chunk
3. **Use Qdrant gRPC** thay vì REST để nhanh hơn 5-10x
4. **Quantization**: Dùng `ScalarQuantization` (int8) để giảm 4x bộ nhớ

## 🎯 Kết luận:

**Ý tưởng của bạn 8.5/10** ⭐  
Chỉ cần thêm bước **Context Expansion** (lấy all siblings) là sẽ giải quyết triệt để vấn đề cross-page!

Bạn cần tôi code mẫu Java cho bước expansion không?

### USER
Hãy đưa ví dụ cho tôi về vấn đề này được khong? ý là mô tả 1 đoạn text sẽ chia chunk ra như thế nào? 

### ASSISTANT
Tuyệt vời! Tôi sẽ tạo một ví dụ cụ thể để bạn hình dung rõ hơn. Giả sử chúng ta có một tài liệu **QUY ĐỊNH THANH TOÁN** như sau:

---

## 📄 **TÀI LIỆU GỐC** (2 trang)

### **Trang 5:**
```
3.2 QUY TRÌNH THANH TOÁN

3.2.1 Hồ sơ thanh toán

Nhà thầu cần chuẩn bị các giấy tờ sau:
- Hóa đơn GTGT gốc
- Biên bản nghiệm thu
- Hợp đồng kinh tế
- Giấy ủy quyền (nếu có)

Thời gian nộp hồ sơ: trong vòng 5 ngày làm việc 
kể từ ngày nghiệm thu.

[Bảng 3.1: Mức phí thanh toán]
┌──────────┬─────────────┬──────────────┐
│ Giá trị  │ Tỷ lệ tạm   │ Thời gian    │
│ hợp đồng │ ứng (%)     │ thanh toán   │
├───────────────────────┼──────────────┤
│ < 100M   │ 30%         │ 3 ngày       │
│ 100-500M │ 50%         │ 5 ngày       │
│ > 500M   │ 70%         │ 7 ngày       │
└──────────┴─────────────┴──────────────┘
```

### **Trang 6:**
```
(nội dung tiếp theo của mục 3.2.1)

Lưu ý quan trọng:
- Hồ sơ phải được đóng dấu giáp lai
- Bản photo phải có công chứng
- Trường hợp khẩn cấp liên hệ: 0909.123.456

3.2.2 Phê duyệt hồ sơ

Phòng Kế toán có trách nhiệm:
- Kiểm tra tính hợp lệ của hóa đơn
- Đối chiếu với hợp đồng đã ký
- Trình Giám đốc phê duyệt

Thời gian phê duyệt: tối đa 2 ngày làm việc.
```

---

## ✂️ **CHIA CHUNK** (theo chiến lược của bạn)

### **Chunk 1: Header + Text đầu tiên**
```json
{
  "chunk_id": "doc001_sec3.2.1_chunk0",
  "parent_id": "doc001_sec3.2.1",
  "doc_id": "doc001",
  "section_header": "3.2.1 Hồ sơ thanh toán",
  "page_range": [5],
  "chunk_type": "header",
  "chunk_order": 0,
  "content": "3.2.1 Hồ sơ thanh toán",
  "is_header_propagated": false,
  "has_continuation": true
}
```

---

### **Chunk 2: Text nội dung (trang 5)**
```json
{
  "chunk_id": "doc001_sec3.2.1_chunk1",
  "parent_id": "doc001_sec3.2.1",
  "doc_id": "doc001",
  "section_header": "3.2.1 Hồ sơ thanh toán",
  "page_range": [5],
  "chunk_type": "text",
  "chunk_order": 1,
  "content": "Nhà thầu cần chuẩn bị các giấy tờ sau:\n- Hóa đơn GTGT gốc\n- Biên bản nghiệm thu\n- Hợp đồng kinh tế\n- Giấy ủy quyền (nếu có)\n\nThời gian nộp hồ sơ: trong vòng 5 ngày làm việc kể từ ngày nghiệm thu.",
  "is_header_propagated": true,
  "has_continuation": true,
  "word_count": 45
}
```

---

### **Chunk 3: Table (trang 5)**
```json
{
  "chunk_id": "doc001_sec3.2.1_chunk2",
  "parent_id": "doc001_sec3.2.1",
  "doc_id": "doc001",
  "section_header": "3.2.1 Hồ sơ thanh toán",
  "page_range": [5],
  "chunk_type": "table",
  "chunk_order": 2,
  "content": "| Giá trị hợp đồng | Tỷ lệ tạm ứng (%) | Thời gian thanh toán |\n|------------------|-------------------|----------------------|\n| < 100M           | 30%               | 3 ngày               |\n| 100-500M         | 50%               | 5 ngày               |\n| > 500M           | 70%               | 7 ngày               |",
  "table_metadata": {
    "title": "Bảng 3.1: Mức phí thanh toán",
    "headers": ["Giá trị hợp đồng", "Tỷ lệ tạm ứng (%)", "Thời gian thanh toán"],
    "num_rows": 3,
    "num_cols": 3
  },
  "is_header_propagated": true,
  "has_continuation": true,
  "word_count": 35
}
```

---

### **Chunk 4: Text tiếp theo (trang 6 - CROSS-PAGE!)**
```json
{
  "chunk_id": "doc001_sec3.2.1_chunk3",
  "parent_id": "doc001_sec3.2.1",
  "doc_id": "doc001",
  "section_header": "3.2.1 Hồ sơ thanh toán",
  "page_range": [6],
  "chunk_type": "text",
  "chunk_order": 3,
  "content": "Lưu ý quan trọng:\n- Hồ sơ phải được đóng dấu giáp lai\n- Bản photo phải có công chứng\n- Trường hợp khẩn cấp liên hệ: 0909.123.456",
  "is_header_propagated": true,
  "has_continuation": false,
  "word_count": 28,
  "cross_page": true
}
```

---

### **Chunk 5: Header mới (3.2.2)**
```json
{
  "chunk_id": "doc001_sec3.2.2_chunk0",
  "parent_id": "doc001_sec3.2.2",
  "doc_id": "doc001",
  "section_header": "3.2.2 Phê duyệt hồ sơ",
  "page_range": [6],
  "chunk_type": "header",
  "chunk_order": 0,
  "content": "3.2.2 Phê duyệt hồ sơ",
  "is_header_propagated": false,
  "has_continuation": true
}
```

---

### **Chunk 6: Text của section mới**
```json
{
  "chunk_id": "doc001_sec3.2.2_chunk1",
  "parent_id": "doc001_sec3.2.2",
  "doc_id": "doc001",
  "section_header": "3.2.2 Phê duyệt hồ sơ",
  "page_range": [6],
  "chunk_type": "text",
  "chunk_order": 1,
  "content": "Phòng Kế toán có trách nhiệm:\n- Kiểm tra tính hợp lệ của hóa đơn\n- Đối chiếu với hợp đồng đã ký\n- Trình Giám đốc phê duyệt\n\nThời gian phê duyệt: tối đa 2 ngày làm việc.",
  "is_header_propagated": true,
  "has_continuation": false,
  "word_count": 38
}
```

---

## 🔍 **KỊCH BẢN TRUY VẤN**

### **User hỏi:** "Hồ sơ thanh toán cần những gì và thời gian là bao lâu?"

**Bước 1: Vector Search** → Tìm thấy:
- Chunk 2 (score: 0.92) - "Nhà thầu cần chuẩn bị..."
- Chunk 4 (score: 0.85) - "Lưu ý quan trọng..."

**Bước 2: Context Expansion**
```java
// Lấy parent_id từ chunk 2: "doc001_sec3.2.1"
// Query tất cả chunks cùng parent_id:
List<Chunk> fullContext = qdrantClient.search(
    filter("parent_id", "doc001_sec3.2.1")
);

// Kết quả trả về:
// - Chunk 0 (header)
// - Chunk 1 (text trang 5)  
// - Chunk 2 (table)
// - Chunk 3 (text trang 6 - CROSS-PAGE!)
```

**Bước 3: Assembly**
```
[SECTION: 3.2.1 Hồ sơ thanh toán]
[PAGES: 5-6]

3.2.1 Hồ sơ thanh toán

Nhà thầu cần chuẩn bị các giấy tờ sau:
- Hóa đơn GTGT gốc
- Biên bản nghiệm thu
- Hợp đồng kinh tế
- Giấy ủy quyền (nếu có)

Thời gian nộp hồ sơ: trong vòng 5 ngày làm việc...

| Giá trị hợp đồng | Tỷ lệ tạm ứng | Thời gian |
| < 100M           | 30%           | 3 ngày    |
| 100-500M         | 50%           | 5 ngày    |
| > 500M           | 70%           | 7 ngày    |

Lưu ý quan trọng:
- Hồ sơ phải được đóng dấu giáp lai
- Bản photo phải có công chứng
- Trường hợp khẩn cấp liên hệ: 0909.123.456
```

**Bước 4: LLM trả lời** ✅
> "Hồ sơ thanh toán cần:
> 1. Hóa đơn GTGT gốc
> 2. Biên bản nghiệm thu  
> 3. Hợp đồng kinh tế
> 4. Giấy ủy quyền (nếu có)
> 
> Thời gian:
> - Nộp hồ sơ: 5 ngày làm việc sau nghiệm thu
> - Thanh toán: 3-7 ngày tùy giá trị (xem bảng trên)
> - Lưu ý: cần đóng dấu giáp lai và công chứng"

---

## 🎯 **ĐIỂM MẤU CHỐT**

1. **Chunk 3 (trang 6)** có `section_header: "3.2.1 Hồ sơ thanh toán"` mặc dù nằm ở trang khác
2. **Cùng `parent_id`** → dễ dàng group khi retrieval
3. **Table giữ nguyên cấu trúc** → AI đọc dễ dàng
4. **`chunk_order`** → sắp xếp đúng thứ tự

Bạn thấy ví dụ này rõ ràng chưa? Tôi có thể tạo thêm ví dụ phức tạp hơn nếu cần!