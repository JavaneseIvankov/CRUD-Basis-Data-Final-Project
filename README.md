# Sistem Manajemen Cafe Nanaz

Aplikasi Java Swing untuk mengelola operasi cafe dengan database SQL Server.

## Fitur Utama

-  **Manajemen Pesanan**: Input pesanan baru, lihat daftar pesanan, layani pesanan
-  **Manajemen Menu**: Kelola item menu dengan harga dan kategori
-  **Manajemen Meja**: Lacak kapasitas dan status meja
-  **Manajemen Pegawai**: Data pegawai yang menangani pesanan
-  **Metode Pembayaran**: Tunai, Kartu Debit, Kartu Kredit, E-Wallet

## Persyaratan

-  Java 21+
-  Docker dan Docker Compose

## Cara Menjalankan Aplikasi

### 1. Persiapan Database

```bash
# Jalankan SQL Server menggunakan Docker
docker-compose up -d

# Tunggu sekitar 30 detik sampai SQL Server siap
```

### 2. Menjalankan Aplikasi

```bash
# Masuk ke folder cafe
cd cafe

# Compile dan jalankan aplikasi
javac -cp ".:lib/*" src/cafe/*.java -d build/classes
java -cp ".:lib/*:build/classes" cafe.View
```

## Pengaturan Database

Aplikasi terhubung ke SQL Server dengan pengaturan:

-  **Host**: localhost:1433
-  **Database**: master
-  **Username**: sa
-  **Password**: YourStrong!Passw0rd

## Struktur Project

```
CRUD-Basis-Data-Final-Project/
├── docker-compose.yml          # Konfigurasi SQL Server
└── cafe/                       # Project Java utama
    ├── src/cafe/
    │   ├── View.java           # GUI utama
    │   ├── CafeDAO.java        # Operasi database
    │   └── DatabaseConnection.java # Koneksi database
    └── lib/
        └── mssql-jdbc-*.jar    # Driver SQL Server
```

## Troubleshooting

**Masalah Koneksi Database:**

-  Pastikan Docker container berjalan: `docker ps`
-  Cek log container: `docker-compose logs mssql`

**Masalah Aplikasi:**

-  Pastikan database sudah di-seed dengan data contoh
-  Cek output console untuk pesan error

---

_Project ini dikembangkan untuk tujuan edukasi sebagai tugas akhir sistem manajemen database._
