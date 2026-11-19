# Ürün kataloğu ve pazaryeri listelemeleri

## Veri modeli

| Kayıt | Sorumluluk |
| --- | --- |
| `ProductFamily` | Sabit model koduyla satılabilir varyantları gruplar. Bir aile adı ve kodu taşır |
| `Product` | Satılabilir ürün veya varyant. Benzersiz SKU, isteğe bağlı GTIN, yerel kategori, fiyat ve stok bu kayıtta bulunur |
| `CatalogDetails` | Varyantın marka, üretici, model, parça numarası, menşe, kondisyon, KDV, paket ölçüleri, görseller ve niteliklerini taşır |
| `MarketplaceListing` | Ürünü bir kanal, satıcı hesabı ve pazarla eşler. Kanal SKU’sunu, kanal barkodunu, kategori, marka ve grup referanslarını, dış kimlikleri, fiyatları ve kanala özel nitelikleri saklar |

Flyway V10 mevcut ürünlerin kimliğini korur ve onlara `KIPS-{id}` biçiminde SKU atar. Sepet ve sipariş kalemleri aynı `product.id` üzerinden çalışır. Eski istemciler SKU göndermeden ürün oluşturabilir. Yeni bir ürün için SKU bir kez belirlenir ve büyük harfe normalize edilir. SKU verilmezse servis benzersiz bir kod üretir.

GTIN metin olarak saklanır ve kontrol basamağı doğrulanır. Eşdeğer sıfır dolgulu gösterimler aynı GTIN kabul edilir. Pazaryerinin kabul ettiği ayrı bir barkod varsa listelemenin `channelBarcode` alanında tutulur. Aileye bağlı varyantlar kendi SKU, fiyat, stok, nitelik ve görsellerini taşır. Dolu bir varyant seçenekleri kombinasyonu aile içinde benzersizdir.

Yerel sepet ve checkout TRY ile çalışır. Listelemedeki satış ve liste fiyatları ilgili kanal/pazar için veridir. Bu fiyatlar yerel sepet toplamını ve ürün stokunu değiştirmez.

## API

| Metot ve yol | Davranış |
| --- | --- |
| `GET /api/v1/product-families`, `GET /api/v1/product-families/{id}` | Ürün ailelerini oku |
| `POST /api/v1/admin/product-families`, `PUT /api/v1/admin/product-families/{id}`, `DELETE /api/v1/admin/product-families/{id}` | Aileyi oluştur, yeniden adlandır veya sil. Varyantı bulunan aile silinemez |
| `GET /api/v1/products` | `familyId`, `categoryId`, `sku`, `gtin` ve `q` filtrelerini birlikte kullan. `q` ad ve SKU içinde arar |
| `POST /api/v1/products`, `PUT /api/v1/products/{id}` | İsteğe bağlı `sku` ve `metadata` kabul eder. Stok başlangıç değeri oluşturmada alınır. Katalog güncellemesi mevcut stok ve gönderilmeyen metadata değerlerini korur |
| `PUT /api/v1/admin/products/{id}/metadata` | Ürünün GTIN, aile ve detay alanlarını değiştir. Boş nesne bunları temizler |
| `GET /api/v1/admin/products/{id}/marketplace-listings` | Bir ürünün kanal eşlemelerini getir |
| `PUT /api/v1/admin/products/{id}/marketplace-listings/{channel}?account=retail&market=TR` | Belirtilen kanal, hesap ve pazar için eşlemeyi oluştur veya değiştir |
| `DELETE /api/v1/admin/products/{id}/marketplace-listings/{channel}?account=retail&market=TR` | Yerel eşlemeyi kaldır |

Aile, metadata ve listeleme yazma işlemleri yönetici yetkisi gerektirir. `account` satıcı hesabı için yerel anahtardır. `market` pazar veya mağaza vitrini anahtarıdır. İkisi de büyük harfe normalize edilir. Kanal SKU’su kanal, hesap ve pazar içinde benzersizdir. Satış fiyatı girildiğinde ISO para birimi girilir. Liste fiyatı satış fiyatının altında olamaz.

Hepsiburada SKU’su büyük harfe normalize edilir ve iç boşluklar reddedilir. Flyway V11 mevcut Hepsiburada SKU’larını dönüştürür. Normalizasyon aynı hesap ve pazarda bir çakışma oluşturuyorsa geçiş durur ve eşlemelerin düzeltilmesi gerekir. Yeni fiyatlar para biriminin alt birimine göre kontrol edilir. Örneğin JPY tam sayı, TRY en fazla iki ve KWD en fazla üç ondalık basamak alır.

Örnek ürün metadata isteği

```json
{
  "familyId": 12,
  "gtin": "4006381333931",
  "details": {
    "brand": "Kips",
    "model": "Linen-24",
    "countryOfOrigin": "TR",
    "condition": "NEW",
    "vatRate": 20,
    "variantOptions": {"color": "Blue", "size": "M"},
    "attributes": {"material": "linen"},
    "imageUrls": ["https://example.com/shirts/linen-blue.jpg"],
    "packageDimensions": {"lengthMm": 280, "widthMm": 210, "heightMm": 40, "weightGrams": 250}
  }
}
```

Örnek Trendyol listeleme kaydı

```json
{
  "channelSku": "LINEN-24-BLUE-M",
  "channelBarcode": "4006381333931",
  "categoryReference": "411",
  "brandReference": "1791",
  "groupReference": "LINEN-24",
  "salePrice": 399.90,
  "listPrice": 499.90,
  "currency": "TRY",
  "channelAttributes": {
    "attributes": [{"attributeId": 338, "attributeValueId": 6980}]
  }
}
```

## Bağlayıcı geliştirme notları

| Kanal | Bağlayıcı için eşleme |
| --- | --- |
| Amazon | Satıcı SKU’su, ASIN ve ürün tipi. Ürün tipi ve pazar bazında şema doğrulaması. Varyantlarda üst SKU kullanımı. [SP-API Listings](https://developer-docs.amazon.com/sp-api/lang-en_EN/docs/manage-product-listings-guide) |
| Hepsiburada | `merchantSku`, kategori, barkod, nitelikler ve varyant grup kimliği. [Hepsiburada katalog API’si](https://developers.hepsiburada.com/tr/companies/hepsiburada?guide=katalog-onemli-bilgiler&product=katalog-urun-entegrasyonu&view=guide) |
| Trendyol | Barkod, `stockCode`, `productMainId`, marka ve kategori kimlikleri ile kategori nitelikleri. [Trendyol Ürün Yaratma V2](https://developers.trendyol.com/docs/%C3%BCr%C3%BCn-yaratma-v2) |
| n11 | Stok kodu, barkod, katalog kimliği, ana ürün kodu ve kategori nitelikleri. Kargo şablonu ve hazırlık süresi kanal niteliklerinde tutulabilir. [n11 Ürün Yükleme](https://developer.n11.com/documentation/n11-marketplace-entegrasyonu/urun-yukleme/) |

Bu sürüm eşlemeleri yerel olarak saklar. Gönderim, kanal şeması doğrulaması, yayın durumu takibi ve stok/fiyat senkronizasyonu bağlayıcıların sorumluluğudur. Satıcı erişim anahtarları ve parolaları listeleme niteliklerine koyulmamalıdır.
