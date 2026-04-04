package com.example.myapplication.data

import com.example.myapplication.models.Category
import kotlinx.coroutines.delay

interface FindRepository {
    suspend fun getCategories(): List<Category>
}

class MockFindRepository : FindRepository {
    override suspend fun getCategories(): List<Category> {
        delay(300)
        
        val homeCategory = Category(id = "home", name = "الرئيسية", isHome = true)

        // Nested structure: Vehicles -> Cars -> Brands -> Models (4 levels)
        val vehiclesCategory = Category(
            id = "vehicles",
            name = "مركبات ولوازمها",
            subItems = listOf(
                Category("v_all", "الكل"),
                Category("v_cars", "سيارات", subItems = listOf(
                    Category("vc_all", "الكل"),
                    Category("vc_toyota", "تويوتا", subItems = listOf(
                        Category("vct_all", "الكل"),
                        Category("vct_camry", "كامري"),
                        Category("vct_corolla", "كورولا")
                    )),
                    Category("vc_lexus", "لكزس"),
                    Category("vc_nissan", "نيسان")
                )),
                Category("v_moto", "دراجات نارية"),
                Category("v_bike", "دراجات هوائية")
            )
        )

        val realEstateCategory = Category(
            id = "real_estate",
            name = "عقارات",
            subItems = listOf(
                Category("re_all", "الكل"),
                Category("re_shops", "محلات"),
                Category("re_villas", "فلل"),
                Category("re_apartments", "شقق")
            )
        )

        return listOf(
            homeCategory,
            realEstateCategory,
            vehiclesCategory,
            Category("electronics", "أجهزة إلكترونية"),
            Category("furniture", "أثاث"),
            Category("sports", "أجهزة رياضية"),
            Category("animals", "حيوانات ولوازمها"),
            Category("other", "أخرى")
        )
    }
}
