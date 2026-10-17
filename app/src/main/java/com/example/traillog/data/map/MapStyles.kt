package com.example.traillog.data.map

import com.example.traillog.data.settings.MapType

/**
 * 地図スタイルの定義。スタイル JSON は assets/styles に置き、タイルは国土地理院の地理院タイルを使う（IF-01）。
 * オフライン地図はタイルの URL で照合されるため、表示とダウンロードで同じスタイルを使うこと。
 */
object MapStyles {
    const val ATTRIBUTION = "出典：国土地理院"

    /** 地理院タイル（標準地図・写真）の最大ズーム。 */
    const val MAX_TILE_ZOOM = 18

    fun styleUri(type: MapType): String = when (type) {
        MapType.STANDARD -> "asset://styles/gsi_std.json"
        MapType.PHOTO -> "asset://styles/gsi_photo.json"
    }

    /** 容量の推定に使う、タイル 1 枚あたりの平均サイズ（おおよその目安）。 */
    fun averageTileBytes(type: MapType): Long = when (type) {
        MapType.STANDARD -> 18_000L
        MapType.PHOTO -> 30_000L
    }
}
