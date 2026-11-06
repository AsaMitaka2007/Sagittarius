package com.dsha.sagittarius.data.weather

enum class WeatherType {
    SUNNY, RAINY, SNOWY, CLOUDY
}

data class WeatherCity(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double
)

object WeatherCities {
    const val FOLLOW_ID = "follow"
    val follow = WeatherCity(FOLLOW_ID, "跟随定位", 0.0, 0.0)
    val defaultCity = WeatherCity("guangzhou", "广州市", 23.1291, 113.2644)

    /** 一二线城市预设，供城市选择与顶栏左右滑动。 */
    val presets: List<WeatherCity> = listOf(
        WeatherCity("beijing", "北京市", 39.9042, 116.4074),
        WeatherCity("shanghai", "上海市", 31.2304, 121.4737),
        WeatherCity("guangzhou", "广州市", 23.1291, 113.2644),
        WeatherCity("shenzhen", "深圳市", 22.5431, 114.0579),
        WeatherCity("chengdu", "成都市", 30.5728, 104.0668),
        WeatherCity("hangzhou", "杭州市", 30.2741, 120.1551),
        WeatherCity("wuhan", "武汉市", 30.5928, 114.3055),
        WeatherCity("xian", "西安市", 34.3416, 108.9398),
        WeatherCity("nanjing", "南京市", 32.0603, 118.7969),
        WeatherCity("chongqing", "重庆市", 29.5630, 106.5516),
        WeatherCity("tianjin", "天津市", 39.3434, 117.3616),
        WeatherCity("suzhou", "苏州市", 31.2989, 120.5853),
        WeatherCity("changsha", "长沙市", 28.2282, 112.9388),
        WeatherCity("zhengzhou", "郑州市", 34.7466, 113.6254),
        WeatherCity("qingdao", "青岛市", 36.0671, 120.3826),
        WeatherCity("shenyang", "沈阳市", 41.8057, 123.4315),
        WeatherCity("dalian", "大连市", 38.9140, 121.6147),
        WeatherCity("xiamen", "厦门市", 24.4798, 118.0894),
        WeatherCity("fuzhou", "福州市", 26.0745, 119.2965),
        WeatherCity("jinan", "济南市", 36.6512, 117.1201),
        WeatherCity("kunming", "昆明市", 25.0389, 102.7183),
        WeatherCity("harbin", "哈尔滨市", 45.8038, 126.5350),
        WeatherCity("changchun", "长春市", 43.8171, 125.3235),
        WeatherCity("urumqi", "乌鲁木齐市", 43.8256, 87.6168),
        WeatherCity("lanzhou", "兰州市", 36.0611, 103.8343),
        WeatherCity("guiyang", "贵阳市", 26.6470, 106.6302),
        WeatherCity("nanning", "南宁市", 22.8170, 108.3665),
        WeatherCity("haikou", "海口市", 20.0440, 110.1999),
        WeatherCity("hohhot", "呼和浩特市", 40.8426, 111.7492),
        WeatherCity("yinchuan", "银川市", 38.4872, 106.2309),
        WeatherCity("xining", "西宁市", 36.6171, 101.7782),
        WeatherCity("lhasa", "拉萨市", 29.6520, 91.1721)
    )

    val all: List<WeatherCity> = listOf(follow) + presets

    fun find(id: String): WeatherCity? = all.firstOrNull { it.id == id }
}
