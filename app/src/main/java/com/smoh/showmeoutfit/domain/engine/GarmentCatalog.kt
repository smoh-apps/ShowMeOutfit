package com.smoh.showmeoutfit.domain.engine

import com.smoh.showmeoutfit.domain.model.Garment
import com.smoh.showmeoutfit.domain.model.LayerCategory

object GarmentCatalog {
    // Base Layers
    val THERMAL_UNDERSHIRT = Garment("base_thermal", "Thermal Undershirt", LayerCategory.BASE, "Insulating base layer to trap body heat")
    val LONG_SLEEVE_TEE = Garment("base_long_sleeve", "Long-sleeve Cotton Shirt", LayerCategory.BASE, "Comfortable full-coverage cotton layer")
    val COTTON_TEE = Garment("base_cotton_tee", "Breathable Cotton T-Shirt", LayerCategory.BASE, "Classic lightweight crewneck t-shirt")
    val POLO_SHIRT = Garment("base_polo", "Cotton Polo Shirt", LayerCategory.BASE, "Smart casual breathable cotton polo")
    val LINEN_TEE = Garment("base_linen_tee", "Lightweight Linen Shirt", LayerCategory.BASE, "Airy linen shirt for warm conditions")
    val ULTRA_LIGHT_TANK = Garment("base_tank", "Ultra-light Linen Tank / Tee", LayerCategory.BASE, "Maximum breathability for extreme heat")

    // Mid Layers
    val WOOL_SWEATER = Garment("mid_wool_sweater", "Wool Sweater", LayerCategory.MID, "Heavy knit wool sweater for cold retention")
    val KNITTED_SWEATER = Garment("mid_knit_sweater", "Knitted Sweater", LayerCategory.MID, "Soft medium-knit sweater")
    val CARDIGAN = Garment("mid_cardigan", "Buttoned Cardigan", LayerCategory.MID, "Easy-to-remove mid layer for temperature swings")
    val HOODIE = Garment("mid_hoodie", "Fleece Hoodie", LayerCategory.MID, "Casual fleece zip-up hoodie")
    val LIGHT_OVERSHIRT = Garment("mid_overshirt", "Light Overshirt", LayerCategory.MID, "Transitional casual overshirt")

    // Outer Layers
    val DOWN_PARKA = Garment("outer_down_parka", "Down Parka", LayerCategory.OUTER, "Heavy insulated down coat for freezing weather")
    val WOOL_COAT = Garment("outer_wool_coat", "Wool Overcoat", LayerCategory.OUTER, "Warm structured winter coat")
    val PUFFER_JACKET = Garment("outer_puffer", "Puffer Jacket", LayerCategory.OUTER, "Insulated puffer jacket")
    val DENIM_JACKET = Garment("outer_denim_jacket", "Denim Jacket", LayerCategory.OUTER, "Sturdy casual denim jacket")
    val LIGHT_JACKET = Garment("outer_light_jacket", "Light Jacket", LayerCategory.OUTER, "Versatile light jacket for cool breezes")
    val WINDBREAKER = Garment("outer_windbreaker", "Technical Windbreaker", LayerCategory.OUTER, "High wind-resistant technical shell")
    val RAINCOAT = Garment("outer_raincoat", "Waterproof Raincoat", LayerCategory.OUTER, "Full waterproof hooded rain jacket")

    // Bottoms
    val THERMAL_PANTS = Garment("bottom_thermal_pants", "Thermal Trousers", LayerCategory.BOTTOM, "Lined heavy winter trousers")
    val JEANS = Garment("bottom_jeans", "Classic Denim Jeans", LayerCategory.BOTTOM, "Versatile durable denim")
    val CHINOS = Garment("bottom_chinos", "Chinos", LayerCategory.BOTTOM, "Smart and breathable cotton trousers")
    val TROUSERS = Garment("bottom_trousers", "Lightweight Trousers", LayerCategory.BOTTOM, "Relaxed lightweight trousers")
    val SHORTS = Garment("bottom_shorts", "Tailored Shorts", LayerCategory.BOTTOM, "Breathable warm-weather shorts")

    // Footwear
    val WINTER_BOOTS = Garment("shoes_winter_boots", "Insulated Winter Boots", LayerCategory.FOOTWEAR, "Grip soles and thermal lining")
    val LEATHER_BOOTS = Garment("shoes_leather_boots", "Leather Boots", LayerCategory.FOOTWEAR, "Durable weather-resistant leather boots")
    val SNEAKERS = Garment("shoes_sneakers", "Everyday Sneakers", LayerCategory.FOOTWEAR, "Comfortable cushioned sneakers")
    val WATERPROOF_SHOES = Garment("shoes_waterproof", "Water-resistant Footwear", LayerCategory.FOOTWEAR, "Treated water-repellent shoes")
    val LOAFERS = Garment("shoes_loafers", "Casual Loafers / Canvas Shoes", LayerCategory.FOOTWEAR, "Slip-on breathable shoes")
    val SANDALS = Garment("shoes_sandals", "Breathable Sandals / Canvas", LayerCategory.FOOTWEAR, "Open airy summer footwear")
}
