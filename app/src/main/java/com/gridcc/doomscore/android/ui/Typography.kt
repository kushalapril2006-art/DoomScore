package com.gridcc.doomscore.android.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.sp
import com.gridcc.doomscore.android.R

/** Bundled OFL fonts: no network provider or Google Play services needed for typography. */
@OptIn(ExperimentalTextApi::class)
object DoomFonts {
    private val weights=listOf(FontWeight.Normal,FontWeight.Medium,FontWeight.SemiBold,FontWeight.Bold)
    val Body=FontFamily(weights.map {weight -> Font(R.font.dm_sans,weight=weight,
        variationSettings=FontVariation.Settings(FontVariation.weight(weight.weight),FontVariation.Setting("opsz",14f)))})
    val Display=FontFamily(weights.map {weight -> Font(R.font.space_grotesk,weight=weight,
        variationSettings=FontVariation.Settings(FontVariation.weight(weight.weight)))})
    private val base=Typography()
    val Type=Typography(
        displayLarge=base.displayLarge.copy(fontFamily=Display,letterSpacing=(-1.5).sp,fontWeight=FontWeight.Bold,fontFeatureSettings="tnum"),
        displayMedium=base.displayMedium.copy(fontFamily=Display,letterSpacing=(-1).sp,fontWeight=FontWeight.Bold,fontFeatureSettings="tnum"),
        displaySmall=base.displaySmall.copy(fontFamily=Display,fontWeight=FontWeight.Bold),
        headlineLarge=base.headlineLarge.copy(fontFamily=Display,fontWeight=FontWeight.Bold,letterSpacing=(-.8).sp),
        headlineMedium=base.headlineMedium.copy(fontFamily=Display,fontWeight=FontWeight.Bold,letterSpacing=(-.5).sp),
        headlineSmall=base.headlineSmall.copy(fontFamily=Display,fontWeight=FontWeight.SemiBold),
        titleLarge=base.titleLarge.copy(fontFamily=Display,fontWeight=FontWeight.SemiBold),
        titleMedium=base.titleMedium.copy(fontFamily=Display,fontWeight=FontWeight.SemiBold),
        titleSmall=base.titleSmall.copy(fontFamily=Body,fontWeight=FontWeight.SemiBold),
        bodyLarge=base.bodyLarge.copy(fontFamily=Body,fontSize=15.sp,lineHeight=23.sp,letterSpacing=0.sp),
        bodyMedium=base.bodyMedium.copy(fontFamily=Body,fontSize=14.sp,lineHeight=21.sp,letterSpacing=0.sp),
        bodySmall=base.bodySmall.copy(fontFamily=Body,fontSize=12.sp,lineHeight=18.sp,letterSpacing=0.sp),
        labelLarge=base.labelLarge.copy(fontFamily=Body,fontWeight=FontWeight.SemiBold,letterSpacing=0.sp),
        labelMedium=base.labelMedium.copy(fontFamily=Body,fontWeight=FontWeight.Medium,letterSpacing=.1.sp),
        labelSmall=base.labelSmall.copy(fontFamily=Body,fontWeight=FontWeight.Medium,letterSpacing=.2.sp)
    )
}
