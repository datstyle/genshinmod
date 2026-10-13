# GenshinMod

Mod vũ khí **Genshin Impact** cho **Minecraft Forge 1.20.1**. Mod thêm các vũ khí 5★ với model 3D riêng, cơ chế chiêu thức, hiệu ứng và âm thanh lấy cảm hứng từ game.

> Đây là mod do fan làm, **không liên kết với HoYoverse**. Genshin Impact và các tên, hình ảnh liên quan thuộc quyền sở hữu của HoYoverse.

| | |
|---|---|
| Minecraft | 1.20.1 |
| Forge | 47.4.10 |
| Java | 17 |
| Mod ID | `genshinmod` |

---

## Vũ khí

### Bloodsoaked Ruins (Giáo của Flins)
`genshinmod:bloodsoaked_ruins` · 5★

- **Nội tại:** mỗi đòn trúng tích 1 điện tích (tối đa 3). Mục tiêu đang ướt nhận thêm sát thương sét.
- **Chuột phải:**
  - Dưới 3 điện tích: lao tới đâm.
  - Đủ 3 điện tích: chiêu dậm. Người dùng bay lên, lơ lửng rồi lao xuống, tạo 3 vòng sát thương điện khớp với âm thanh.
- **Bản phù phép:** chiêu dậm mạnh hơn (sát thương x1.5, bán kính x1.3) và có hiệu ứng vẽ bằng hình học thật ở client, bản thường dùng hạt.
- Ô hotbar và túi đồ sáng lên khi đủ điện tích. Giáo được đeo sau lưng khi không cầm trên tay.
- **Cách có được:** chỉ xuất hiện trong rương công trình (tỉ lệ 10%), **không có công thức chế tạo**.

### Staff of Homa (Trượng Hộ Ma)
`genshinmod:staff_of_homa` · 5★ · Trường thương

- **Nội tại:** đòn trúng thiêu đốt mục tiêu và tích 1 **Linh Hỏa** (tối đa 3). Khi máu người cầm từ 50% trở xuống, đòn đánh gây thêm sát thương lửa.
- **Chuột phải khi đủ 3 Linh Hỏa:** bùng nổ lửa diện rộng quanh người, đẩy lùi và thiêu đốt địch xung quanh.
- Tầm đánh dài hơn một chút so với kiếm thường.
- Có bộ âm thanh riêng cho vung, đánh trúng, nhận Linh Hỏa, đủ 3 Linh Hỏa và bùng nổ.

### Hệ thống chung
- Tooltip và màu tên theo độ hiếm (4★ tím, 5★ vàng, 5★ siêu hiếm đỏ).
- Mod có model 3D tùy chỉnh, giáo và trượng hiển thị cả khi đeo sau lưng.

---

## Cài đặt (người chơi)

1. Cài **Minecraft 1.20.1** và **Forge 47.4.10**.
2. Tải file `.jar` mới nhất ở mục **Releases** (hoặc tự build, xem bên dưới).
3. Bỏ file `.jar` vào thư mục `mods` rồi vào game.

## Build từ mã nguồn (nhà phát triển)

Yêu cầu: JDK 17.

```powershell
# Build ra file jar (nằm trong build\libs)
.\gradlew build

# Chạy client để thử
.\gradlew runClient

# Chạy server riêng để kiểm tra mod có chạy ổn phía server không
.\gradlew runServer
```

Lần đầu chạy `runServer` cần mở `run\eula.txt` và đổi `eula=false` thành `eula=true`.

## Cấu trúc thư mục

```
src/main/java/com/dat/genshinmod/
├── item/      Vũ khí, ModItems, tab sáng tạo, độ hiếm
├── combat/    Chiêu dậm, xung điện (server)
├── client/    Render, hiệu ứng, đeo sau lưng, tooltip (client)
├── network/   Gói tin giữa server và client
├── sound/     Đăng ký âm thanh
└── loot/      Loot modifier cho rương công trình
src/main/resources/
├── assets/genshinmod/   Model, texture, âm thanh, ngôn ngữ
└── data/                Loot modifier
```

## Báo lỗi

Mở **Issues** trên GitHub, ghi rõ: phiên bản mod, phiên bản Forge, danh sách mod đang dùng và file `logs/latest.log`.

## Tác giả

**Đạt** ([@datstyle](https://github.com/datstyle))

## Giấy phép

Chưa chọn giấy phép. Khi chưa có file `LICENSE`, mặc định mã nguồn vẫn thuộc về tác giả, người khác chưa được tự do sử dụng lại. Muốn mở mã nguồn thì thêm file `LICENSE` (ví dụ MIT).