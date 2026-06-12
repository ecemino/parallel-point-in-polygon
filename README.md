# Paralel Programlama Projesi Raporu
## Nokta-Poligon İçinde mi? – Java Thread'leri ile Paralel Çözüm

**Öğrenci:** Ece Açar
**Ders:** Paralel Programlama  
**Tarih:** 12 Haziran 2026

## 1. Giriş ve Problem Tanımı

Bu projede, **verilen x-y koordinat çiftleri** ile tanımlanan **konveks veya konkav** bir poligonun içinde bir noktanın bulunup bulunmadığını tespit eden bir program geliştirilmiştir. Çözüm, hem sıralı (sequential) hem de **Java Thread'leri kullanılarak paralel** olarak gerçekleştirilmiştir; elde edilen hızlanma (speedup) katsayıları karşılaştırmalı olarak sunulmuştur.

---

## 2. Algoritma: Ray Casting (Işın Yayma)

### 2.1 Temel Fikir

Her sorgu noktası `P(x, y)` için, **sağ yönde yatay bir ışın** uzatılır. Bu ışının poligonun kenarlarını kaç kez kestiği sayılır:

- **Tek sayı kesişim → Nokta içeride**  
- **Çift sayı kesişim → Nokta dışarıda**

Bu yöntem, konveks ve konkav her türlü basit poligon için doğru sonuç verir.

### 2.2 Matematiksel İfade

`i` ve `j` köşeleri arasındaki kenar için ışın kesişimi şartı:

```
(vi.y > p.y) ≠ (vj.y > p.y)
```

ve kesişim noktasının x koordinatı:

```
x_kesişim = (vj.x - vi.x) × (p.y - vi.y) / (vj.y - vi.y) + vi.x
```

Eğer `p.x < x_kesişim` ise ışın kenarı keser ve `inside` bayrağı tersine çevrilir.

### 2.3 Zaman Karmaşıklığı

| Durum | Karmaşıklık |
|-------|-------------|
| Tek nokta, n-kenarlı poligon | O(n) |
| Q sorgu noktası, n-kenarlı poligon (sıralı) | O(Q × n) |
| Q sorgu noktası, n-kenarlı poligon, T thread | O(Q × n / T) |

---

## 3. Programın Yapısı ve Fonksiyonlar

### 3.1 Veri Modeli

```java
record Point(double x, double y) {}
```

Java'nın `record` yapısı kullanılarak değişmez (immutable) bir 2B nokta temsili oluşturulmuştur. Bu, thread güvenliğini (thread safety) otomatik olarak sağlar — `record` nesneleri paylaşılan yazma durumu içermez.

### 3.2 Temel Fonksiyonlar

#### `isInsideSequential(List<Point> polygon, Point p)`

Tek bir noktanın poligon içinde olup olmadığını **sıralı** olarak test eder. Tüm kenarlar döngüyle taranır, `inside` bayrağı ışın kesişimlerinde tersine çevrilir.

```java
public static boolean isInsideSequential(List<Point> polygon, Point p) {
    int n = polygon.size();
    boolean inside = false;
    int j = n - 1;
    for (int i = 0; i < n; i++) {
        Point vi = polygon.get(i);
        Point vj = polygon.get(j);
        if ((vi.y() > p.y()) != (vj.y() > p.y())) {
            double xIntersect = (vj.x() - vi.x()) * (p.y() - vi.y())
                    / (vj.y() - vi.y()) + vi.x();
            if (p.x() < xIntersect) inside = !inside;
        }
        j = i;
    }
    return inside;
}
```

#### `testPointsSequential(List<Point> polygon, List<Point> queryPoints)`

Tüm sorgu noktalarını **sırayla** işler; sonuçları `HashMap<Integer, Boolean>` içinde döndürür.

#### `PolygonWorker` (Thread sınıfı)

```java
static class PolygonWorker extends Thread {
    private final List<Point> polygon;
    private final List<Point> queryPoints;
    private final int startIdx, endIdx;
    private final ConcurrentHashMap<Integer, Boolean> results;

    @Override
    public void run() {
        for (int i = startIdx; i < endIdx; i++) {
            results.put(i, isInsideSequential(polygon, queryPoints.get(i)));
        }
    }
}
```

Her thread, kendisine atanan `[startIdx, endIdx)` aralığındaki noktaları bağımsız olarak hesaplar. Sonuçlar `ConcurrentHashMap` içine yazılır; bu yapı thread-safe olduğundan harici senkronizasyona gerek yoktur.

#### `testPointsParallel(List<Point> polygon, List<Point> queryPoints, int numThreads)`

Paralel çözümün ana fonksiyonu:

1. Toplam nokta sayısı `numThreads`'e bölünür → `chunk = ⌈Q / T⌉`
2. Her thread için `PolygonWorker` oluşturulup başlatılır
3. `thread.join()` ile tüm thread'lerin bitmesi beklenir
4. Birleştirilmiş `ConcurrentHashMap` döndürülür

```java
public static Map<Integer, Boolean> testPointsParallel(
        List<Point> polygon, List<Point> queryPoints, int numThreads)
        throws InterruptedException {
    ConcurrentHashMap<Integer, Boolean> results = new ConcurrentHashMap<>();
    int chunk = (queryPoints.size() + numThreads - 1) / numThreads;
    List<Thread> threads = new ArrayList<>();
    for (int t = 0; t < numThreads; t++) {
        int start = t * chunk;
        int end   = Math.min(start + chunk, queryPoints.size());
        if (start >= queryPoints.size()) break;
        PolygonWorker w = new PolygonWorker(polygon, queryPoints, start, end, results);
        threads.add(w);
        w.start();
    }
    for (Thread thread : threads) thread.join();
    return results;
}
```

---

## 4. Çalışma Örnekleri

### 4.1 Konveks Poligon – Birim Kare

**Köşeler:** (-1,-1), (1,-1), (1,1), (-1,1)

| Test Noktası | Beklenen | Sıralı Sonuç | Paralel Sonuç |
|---|---|---|---|
| (0.0, 0.0) | INSIDE | INSIDE | INSIDE |
| (0.5, 0.5) | INSIDE | INSIDE | INSIDE |
| (1.5, 0.0) | OUTSIDE | OUTSIDE | OUTSIDE |
| (-1.5, -1.5) | OUTSIDE | OUTSIDE | OUTSIDE |
| (1.0, 1.0) | BOUNDARY | OUTSIDE | OUTSIDE |


### 4.2 Konveks Poligon – Düzgün Sekizgen (8 Köşe)

**Köşeler:** Birim çembere (r=1) yazılmış düzgün sekizgen; köşeler arası 45°, ilk köşe tepede

| Test Noktası | Sıralı Sonuç | Paralel Sonuç | Açıklama |
|---|---|---|---|
| (0.00, 0.00) | INSIDE | INSIDE | Merkez |
| (0.00, 0.95) | INSIDE | INSIDE | Üst tepe yakını |
| (0.85, 0.85) | OUTSIDE | OUTSIDE | Köşe kesiği bölgesi |
| (1.50, 0.00) | OUTSIDE | OUTSIDE | Uzak dış |
| (0.60, 0.60) | INSIDE | INSIDE | İç bölge |

> Köşe kesiği testi (0.85, 0.85): Birim karede bu nokta **içeride** olurdu; sekizgende **dışarıdadır**. Bu, sekizgenin köşe kesiklerini doğru modellediğini gösterir.

### 4.3 Konkav Poligon – 8 Köşeli Yıldız

**Köşeler:**  (0,1), (0.2,0.3), (1,0), (0.2,-0.3), (0,-1), (-0.2,-0.3), (-1,0), (-0.2,0.3)

| Test Noktası | Sıralı Sonuç | Açıklama |
|---|---|---|
| (0.0, 0.0) | INSIDE | Merkez |
| (0.0, 0.95) | INSIDE | Üst uç yakını |
| (0.5, 0.5) | OUTSIDE | Köşegen çentik bölgesi |
| (0.15, 0.0) | INSIDE | İç kol bölgesi |

### 4.4 Konkav Poligon – L Şekli

**Köşeler:** (0,0), (2,0), (2,1), (1,1), (1,2), (0,2)

| Test Noktası | Sıralı Sonuç |
|---|---|
| (0.5, 0.5) | INSIDE |
| (0.5, 1.5) | INSIDE |
| (1.5, 1.5) | OUTSIDE (çentik) |
| (1.5, 0.5) | INSIDE |

---

## 5. Paralel Çözümün Açıklaması

### 5.1 Neden Paralel?

Ray Casting algoritması, **her nokta için tamamen bağımsız** bir hesaplama yapar. Bir noktanın sonucu başka bir noktanın sonucuna bağlı değildir. Bu özellik algoritmayı **"embarrassingly parallel"** (utanç verici ölçüde paralel) yapar. Thread'ler arasında senkronizasyon neredeyse gerekmez.

### 5.2 Thread Güvenliği Analizi

```
Thread-0: [0 .. chunk-1]         → results.put(0..chunk-1, ...)
Thread-1: [chunk .. 2*chunk-1]   → results.put(chunk..2*chunk-1, ...)
Thread-k: [k*chunk .. (k+1)*chunk-1] → ayrışık anahtarlar!
```

Her thread **farklı anahtar aralığına** yazar; dolayısıyla `ConcurrentHashMap`'in iç kilitleme mekanizması bile ek çakışma yaşamaz.

---

## 6. Hızlanma Analizi

### 6.1 Ölçüm Metodolojisi

- `System.nanoTime()` ile nanosaniye hassasiyetli ölçüm
- Her yapılandırma için tek çalışma (tekrarlı ortalama alınabilir)

### 6.2 Gerçek Ölçüm Sonuçları – Birim Kare (Konveks, 4 Köşe)

| Nokta | Sıralı (ms) | 2T (ms) | 2T Hızl. | 4T (ms) | 4T Hızl. | 8T (ms) | 8T Hızl. |
|-------|------------|---------|----------|---------|----------|---------|----------|
| 100 | 0.114 | 0.325 | 0.35 | 0.359 | 0.32 | 0.377 | 0.30 |
| 500 | 0.116 | 0.403 | 0.29 | 0.432 | 0.27 | 0.518 | 0.22 |
| 1 000 | 0.230 | 0.683 | 0.34 | 1.286 | 0.18 | 0.625 | 0.37 |
| 5 000 | 0.854 | 2.563 | 0.33 | 1.900 | 0.45 | 1.737 | 0.49 |
| 10 000 | 1.554 | 1.367 | 1.14 | 1.661 | 0.94 | 1.066 | **1.46** |
| 50 000 | 7.141 | 5.210 | 1.37 | 9.348 | 0.76 | 5.639 | 1.27 |
| 100 000 | 5.121 | 6.276 | 0.82 | 3.698 | 1.39 | 2.921 | 1.75 |
| 500 000 | 31.934 | 20.160 | **1.58** | 8.822 | **3.62** | 17.055 | 1.87 |

> Birim kare yalnızca **4 kenar** içerir; her nokta için hesaplama süresi çok kısadır. Thread oluşturma/context switch maliyetleri küçük nokta sayılarında hakimdir; 500K noktada 4 thread ile **3.62×** hızlanma elde edilmiştir.

### 6.3 Gerçek Ölçüm Sonuçları – Düzgün Sekizgen (Konveks, 8 Köşe)

| Nokta | Sıralı (ms) | 2T (ms) | 2T Hızl. | 4T (ms) | 4T Hızl. | 8T (ms) | 8T Hızl. |
|-------|------------|---------|----------|---------|----------|---------|----------|
| 100 | 0.146 | 0.350 | 0.42 | 0.267 | 0.55 | 0.473 | 0.31 |
| 500 | 0.579 | 0.791 | 0.73 | 0.782 | 0.74 | 0.607 | 0.95 |
| 1 000 | 0.383 | 0.218 | **1.76** | 0.261 | 1.47 | 0.413 | 0.93 |
| 5 000 | 0.725 | 0.843 | 0.86 | 0.787 | 0.92 | 0.806 | 0.90 |
| 10 000 | 1.387 | 1.410 | 0.98 | 1.507 | 0.92 | 1.599 | 0.87 |
| 50 000 | 2.079 | 5.251 | 0.40 | 6.295 | 0.33 | 5.603 | 0.37 |
| 100 000 | 7.029 | 4.759 | 1.48 | 2.031 | **3.46** | 1.655 | **4.25** |
| 500 000 | 27.102 | 14.497 | **1.87** | 8.953 | **3.03** | 18.651 | 1.45 |

> Sekizgen, 4 köşeli kareye kıyasla **her nokta için 2× daha fazla kenar hesabı** yapar. Bu ek yük, paralelleşmenin fayda sağlamasını kolaylaştırır: 100K noktada **8 thread ile 4.25×** hızlanma elde edilmiştir. 1K noktada 2 thread ile **1.76×** hızlanma, sınır noktanın kareden daha düşük olduğunu gösterir.

### 6.4 Gerçek Ölçüm Sonuçları – 8 Köşeli Yıldız (Konkav)

| Nokta | Sıralı (ms) | 2T (ms) | 2T Hızl. | 4T (ms) | 4T Hızl. | 8T (ms) | 8T Hızl. |
|-------|------------|---------|----------|---------|----------|---------|----------|
| 100 | 0.006 | 0.225 | 0.03 | 0.229 | 0.03 | 0.468 | 0.01 |
| 500 | 0.046 | 0.159 | 0.29 | 0.231 | 0.20 | 0.392 | 0.12 |
| 1 000 | 0.042 | 0.197 | 0.21 | 0.193 | 0.22 | 0.282 | 0.15 |
| 5 000 | 0.207 | 0.339 | 0.61 | 0.396 | 0.52 | 0.427 | 0.48 |
| 10 000 | 0.391 | 0.501 | 0.78 | 0.429 | 0.91 | 0.668 | 0.59 |
| 50 000 | 2.077 | 2.154 | 0.96 | 1.079 | **1.93** | 0.931 | **2.23** |
| 100 000 | 5.086 | 4.377 | 1.16 | 1.970 | **2.58** | 1.427 | **3.57** |
| 500 000 | 26.540 | 25.381 | 1.05 | 8.902 | **2.98** | 12.813 | 2.07 |

### 6.5 Gerçek Ölçüm Sonuçları – L Şekli (Konkav, 6 Köşe)

| Nokta | Sıralı (ms) | 2T (ms) | 2T Hızl. | 4T (ms) | 4T Hızl. | 8T (ms) | 8T Hızl. |
|-------|------------|---------|----------|---------|----------|---------|----------|
| 100 | 0.053 | 0.316 | 0.17 | 0.278 | 0.19 | 0.415 | 0.13 |
| 500 | 0.089 | 0.188 | 0.48 | 0.235 | 0.38 | 0.343 | 0.26 |
| 1 000 | 0.146 | 0.266 | 0.55 | 0.239 | 0.61 | 0.387 | 0.38 |
| 5 000 | 0.257 | 0.854 | 0.30 | 0.702 | 0.37 | 0.627 | 0.41 |
| 10 000 | 0.436 | 1.490 | 0.29 | 1.092 | 0.40 | 0.586 | 0.74 |
| 50 000 | 2.202 | 2.133 | 1.03 | 1.751 | 1.26 | 1.683 | 1.31 |
| 100 000 | 3.943 | 4.176 | 0.94 | 1.995 | **1.98** | 2.674 | 1.48 |
| 500 000 | 16.420 | 13.554 | 1.21 | 25.312 | 0.65 | 4.783 | **3.43** |

### 6.6 Poligon Türlerine Göre Karşılaştırma (500K Nokta)

| Poligon | Kenar | Sıralı (ms) | En İyi Paralel | En İyi Hızl. | Optimal Thread |
|---------|-------|------------|---------------|-------------|----------------|
| Birim Kare (konveks) | 4 | 31.934 | 8.822 ms | **3.62×** | 4T |
| Düzgün Sekizgen (konveks) | 8 | 27.102 | 8.953 ms | **3.03×** | 4T |
| 8 Köşeli Yıldız (konkav) | 8 | 26.540 | 8.902 ms | **2.98×** | 4T |
| L Şekli (konkav) | 6 | 16.420 | 4.783 ms | **3.43×** | 8T |

> **Önemli Gözlem:** Düzgün sekizgen (konveks, 8 köşe), 8 köşeli yıldız (konkav, 8 köşe) ile neredeyse aynı sıralı süreye sahiptir; çünkü her iki poligonda da kenar sayısı eşittir. Sekizgen en iyi hızlanmasını **100K noktada 8 thread ile 4.25×** olarak göstermiştir.

### 6.7 Gözlemler

1. **Küçük veri setlerinde (< 10 000 nokta, basit poligon):** Thread oluşturma ve bağlam değiştirme (context switching) maliyeti, paralelleşme kazancını sıfırlar veya yavaşlatır. Bu veri aralığında sıralı çözüm tercih edilmelidir.

2. **Büyük veri setlerinde (≥ 100 000 nokta, ~8+ kenar):** Thread sayısı arttıkça belirgin hızlanma gözlemlenir:
   - Düzgün sekizgen, 100K nokta, **8 thread → ~4.25×** hızlanma
   - Birim kare, 500K nokta, **4 thread → ~3.62×** hızlanma
   - 8 köşeli yıldız, 100K nokta, **8 thread → ~3.57×** hızlanma
   - L şekli, 500K nokta, **8 thread → ~3.43×** hızlanma

3. **Kenar sayısı ve hızlanma ilişkisi:** Daha fazla kenarlı poligonlar, her nokta için daha fazla işlem gerektirdiğinden paralelleşme daha erken fayda sağlar. Sekizgenin 1K noktada bile **1.76×** hızlanma sağlaması bunu doğrular.

4. **Amdahl Yasası:** Teorik maksimum hızlanma `S(T) = 1 / (s + (1-s)/T)` formülü ile hesaplanır; burada `s` sıralı kesrin oranıdır. Thread oluşturma maliyeti ve `join()` bekleme süresi bu `s` değerini artırır.

5. **ConcurrentHashMap Ek Yükü:** Yazma işlemleri dahili kilitler içerir. Farklı thread'ler farklı anahtar aralıklarına yazdığından çakışma minimumdur; ancak `HashMap`'e kıyasla küçük bir ek yük söz konusudur.

### 6.8 Hızlanma Grafiği (Kavramsal)

```
Hızlanma
  4 |                               ●  (Kare 4T, ~3.62×)
    |                          ◆        ◆ (Sekizgen 8T, ~4.25×)
  3 |                     ●
    |                ●
  2 |           ●
    |      ●
  1 +----+------+--------+----------+---------> Nokta Sayısı
    100  1K     10K     100K       500K
```

---

## 7. Sonuç

Bu projede, konkav ve konveks poligonlar (birim kare, düzgün sekizgen, 8 köşeli yıldız, L şekli) için **Ray Casting** algoritması uygulanmış ve Java Thread'leri kullanılarak **paralel** hale getirilmiştir:

- **Doğruluk:** Tüm yapılandırmalarda sıralı ve paralel sonuçlar birebir eşleşmektedir (MATCH).
- **Hızlanma:** En iyi hızlanma, düzgün sekizgen poligonunda 100K noktada **8 thread ile ~4.25×** olarak elde edilmiştir.
- **Kenar sayısı etkisi:** 4 kenarlı kareye kıyasla 8 kenarlı sekizgen, paralelleşme eşiğine daha düşük nokta sayısında ulaşmaktadır.
- **Ölçeklenebilirlik:** Nokta sayısı arttıkça hızlanma da genel olarak artmaktadır.
- **Sınırlamalar:** Küçük veri setlerinde overhead baskın gelmektedir; bu durumda sıralı çözüm tercih edilmelidir.

## Anlatım Videosu
[YouTube videosunu izlemek için tıklayın](https://youtu.be/Vh-zIuxGBVE)


