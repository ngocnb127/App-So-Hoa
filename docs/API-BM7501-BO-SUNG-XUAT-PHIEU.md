# BỔ SUNG — Trạng thái "ĐÃ XUẤT PHIẾU" của BM 75.01

**Gửi:** đội FMS API
**Từ:** đội app tra nạp (Android)
**Ngày:** 2026-09-23
**Bổ sung cho:** `API-BM7501-YEU-CAU-BACKEND.md` (bản 2) và `API-BM7501-PHAN-HOI-BACKEND.md`

API các anh làm đã khớp. Đây là **một nghiệp vụ mới phát sinh sau đó**, cần server hiểu thêm
đúng một giá trị trạng thái và hai trường đi kèm — không đổi endpoint, không đổi định dạng.

---

## 1. Nghiệp vụ

Trước đây phiếu 75.01 không có điểm chốt: nhân viên sửa và in lại bao nhiêu lần cũng được.
Chủ dự án chốt ngày 2026-09-23 thêm nút **"Xuất phiếu"** trên màn nhập:

| Giai đoạn | Nhân viên làm được gì |
|---|---|
| **Chưa xuất** (`DRAFT`) | Sửa thoải mái, in thử bao nhiêu lần cũng được |
| **Đã xuất** (`EXPORTED`) | **Không sửa được nữa**, chỉ in lại được |

Sau khi xuất, **danh sách mẻ hút** trên app hiện nhãn `Đã xuất (<số phiếu>)` ở đúng mẻ đó.

**Vì sao server cần biết:** trạng thái `EXPORTED` là tín hiệu để hệ thống **đẩy phiếu sang
Omega**. Phiếu còn `DRAFT` là phiếu đang làm dở, chưa được chuyển đi.

---

## 2. Thay đổi trong payload — `POST api/bm7501/post2`

Chỉ thêm **một giá trị trạng thái** và **hai trường**, vẫn multipart và PascalCase như cũ:

```jsonc
{
  // …các trường như bản 2…
  "BusinessStatus": "EXPORTED",          // MỚI: DRAFT | EXPORTED | CANCELLED
  "ExportedAt": "2026-09-23T10:20:00",   // MỚI: mốc chốt sổ, null khi chưa xuất
  "ExportedByUserId": 42                 // MỚI: nhân viên bấm xuất; 0 khi chưa xuất
}
```

| Trường | Kiểu | Ý nghĩa |
|---|---|---|
| `BusinessStatus = "EXPORTED"` | chuỗi | Phiếu đã chốt sổ → **đủ điều kiện đẩy sang Omega** |
| `ExportedAt` | ngày giờ | Thời điểm nhân viên bấm Xuất, giờ máy tablet |
| `ExportedByUserId` | số | Nhân viên bấm Xuất (có thể khác người nhập, khác người khai ở mục A) |

Ba giá trị trạng thái app gửi lên từ nay: `DRAFT`, `EXPORTED`, `CANCELLED`. Các giá trị cũ của
bản thử nghiệm (`A_DONE`, `SIGNED`, `PRINTED`…) vẫn có thể xuất hiện ở vài tablet, như §4.3 bản 2.

---

## 3. Việc server cần làm

### 3.1 Điều kiện đẩy sang Omega

Đẩy khi **`BusinessStatus = "EXPORTED"`**. Không đẩy phiếu `DRAFT` (đang làm dở) và `CANCELLED`.

Khi xuất, app đã bắt buộc phiếu **đủ mọi trường bắt buộc của mục A/B/C** và **có `LocalNumber`**
— không có số phiếu thì app không cho xuất. Nên phiếu `EXPORTED` luôn có số.

⚠️ **Phiếu `EXPORTED` có thể KHÔNG có ảnh chữ ký.** Luồng thật là in phiếu ra rồi hai bên ký
tay trên giấy, nên app chỉ **cảnh báo** chứ không chặn khi chưa ký trên tablet (chốt
2026-09-23). Server đừng coi "thiếu ảnh chữ ký" là dữ liệu hỏng và đừng chặn đẩy Omega vì lý do
đó; `…SignatureSha256` tương ứng cũng sẽ là `null`.

### 3.2 `EXPORTED` là một chiều

App không có nút "mở lại phiếu đã xuất". Vì vậy:

- Phiếu đã `EXPORTED` **không bao giờ** được app gửi lại với `BusinessStatus = "DRAFT"`.
- Nếu server nhận `DRAFT` cho một phiếu đang `EXPORTED`: đó là **dữ liệu lạ** (bản cũ kẹt
  trong hàng đợi của một tablet khác). Đề nghị **giữ nguyên `EXPORTED`**, ghi cảnh báo vào
  `Message`, vẫn trả 200.
- Chỉ `CANCELLED` mới được phép đến sau `EXPORTED` (huỷ phiếu đã xuất — hiện app chưa làm nút
  này, nhưng để ngỏ).

### 3.3 Nội dung sau khi xuất

Nội dung phiếu `EXPORTED` **không đổi nữa** — app khoá ở tầng ghi cục bộ, mọi lệnh ghi nội dung
đều bị từ chối. Nếu server thấy nội dung khác với bản `EXPORTED` đã lưu thì đó cũng là bản cũ
kẹt hàng đợi: giữ bản `EXPORTED`, cảnh báo trong `Message`.

Ảnh chữ ký cũng vậy: sau khi xuất app không ký lại được nữa — phiếu nào xuất mà chưa ký trên
máy thì vĩnh viễn không có ảnh chữ ký, chữ ký nằm trên tờ giấy đã in.

### 3.4 Trả về ở `GET api/bm7501/get2/{truckId}`

Đề nghị trả thêm `ExportedAt`, `ExportedByUserId` cùng `BusinessStatus` (đã có sẵn), để app khôi
phục đúng trạng thái khoá khi đổi/cài lại máy. Nếu thiếu, app khôi phục xong sẽ tưởng phiếu vẫn
sửa được.

Nếu có thêm trường cho biết **đã đẩy sang Omega hay chưa** (ví dụ `OmegaStatus`, `OmegaSentAt`)
thì app hiện được cho nhân viên biết phiếu đã sang Omega — không bắt buộc, nhưng hữu ích khi đối soát.

---

## 4. Phía app đã làm xong

| Hạng mục | Trạng thái |
|---|---|
| Nút **Xuất phiếu** + xác nhận trước khi chốt | ✅ |
| Khoá nội dung sau khi xuất (kể cả autosave), vẫn in lại được | ✅ ở tầng ghi DB, không chỉ ở giao diện |
| Bắt buộc đủ thông tin mục A/B/C + có số phiếu mới cho xuất | ✅ |
| Thiếu chữ ký: cảnh báo bằng popup "Vẫn xuất?", không chặn | ✅ |
| Danh sách mẻ hút hiện `Đã xuất (<số phiếu>)` | ✅ chỉ ở danh sách mẻ hút, không ở danh sách tra nạp |
| Đẩy trạng thái lên server qua `post2` | ✅ phiếu tự vào hàng đợi khi xuất |
| Khôi phục trạng thái khi cài lại máy (`get2`) | ⏸ chờ mục 3.4 |

---

## 5. Câu hỏi

1. Server nhận `EXPORTED` và dùng làm điều kiện đẩy Omega được luôn chứ, hay cần thêm bước
   duyệt nào phía hệ thống?
2. `get2` bổ sung `ExportedAt` / `ExportedByUserId` được không (mục 3.4)?
3. Có cần app gửi thêm gì để Omega nhận đủ (mã khách hàng, mã kho, loại nghiệp vụ…)? Hiện app
   gửi đúng những gì có trên biểu mẫu cộng các khoá liên kết ở §2.4 bản 2.
4. Phiếu `EXPORTED` mà không có ảnh chữ ký (ký tay trên giấy) có ảnh hưởng gì tới Omega không?
5. Khi phiếu đã sang Omega rồi mà nhân viên phát hiện sai thì quy trình là gì — huỷ phiếu và
   lập phiếu mới (revision), hay sửa bên Omega? App đang chặn cứng việc sửa sau khi xuất.
