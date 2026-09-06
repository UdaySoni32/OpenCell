package com.example.opencell.ui.theme

import androidx.compose.ui.graphics.Color

// Brand palette (teal). The default-dialer space is dominated by blue/green
// stock dialers; teal keeps OpenCell visually distinct while staying calm.
private val Brand10 = Color(0xFF062E31)
private val Brand20 = Color(0xFF0F4C50)
private val Brand30 = Color(0xFF14565C)
private val Brand40 = Color(0xFF1B6E75)
private val Brand80 = Color(0xFF7FD5DA)
private val Brand90 = Color(0xFFB2ECF1)

private val BrandSurface2 = Color(0xFFE1E4E7)
private val BrandSurface7 = Color(0xFF40484C)

// Semantic status colors used across call UI and lists.
// Success/call-green is centralized here so every screen uses one token
// instead of scattered hard-coded 0xFF2E7D32 values.
val CallGreenLight = Color(0xFF1B7F45)
val CallGreenDark = Color(0xFF69DFA0)
val MissedRed = Color(0xFFBA1A1A)

val LightPrimary = Brand40
val LightOnPrimary = Color.White
val LightPrimaryContainer = Brand90
val LightOnPrimaryContainer = Brand10
val LightSecondary = Color(0xFF4A6367)
val LightOnSecondary = Color.White
val LightSecondaryContainer = Color(0xFFCCE8EC)
val LightOnSecondaryContainer = Color(0xFF051F22)
val LightTertiary = Color(0xFF4B59A9)
val LightOnTertiary = Color.White
val LightTertiaryContainer = Color(0xFFDEE1FF)
val LightOnTertiaryContainer = Color(0xFF00174B)
val LightError = MissedRed
val LightOnError = Color.White
val LightErrorContainer = Color(0xFFFFDAD6)
val LightOnErrorContainer = Color(0xFF410002)
val LightBackground = Color(0xFFF6FAFA)
val LightOnBackground = Color(0xFF171D1E)
val LightSurface = Color(0xFFF6FAFA)
val LightOnSurface = Color(0xFF171D1E)
val LightSurfaceVariant = BrandSurface2
val LightOnSurfaceVariant = Color(0xFF3F484A)
val LightOutline = Color(0xFF6F797B)

val DarkPrimary = Brand80
val DarkOnPrimary = Brand20
val DarkPrimaryContainer = Brand30
val DarkOnPrimaryContainer = Brand90
val DarkSecondary = Color(0xFFB0CCD1)
val DarkOnSecondary = Color(0xFF1B3438)
val DarkSecondaryContainer = Color(0xFF324B4F)
val DarkOnSecondaryContainer = Color(0xFFCCE8EC)
val DarkTertiary = Color(0xFFBCC3FF)
val DarkOnTertiary = Color(0xFF1A2B78)
val DarkTertiaryContainer = Color(0xFF334290)
val DarkOnTertiaryContainer = Color(0xFFDEE1FF)
val DarkError = Color(0xFFFFB4AB)
val DarkOnError = Color(0xFF690005)
val DarkErrorContainer = Color(0xFF93000A)
val DarkOnErrorContainer = Color(0xFFFFDAD6)
val DarkBackground = Color(0xFF0E1415)
val DarkOnBackground = Color(0xFFDEE3E4)
val DarkSurface = Color(0xFF0E1415)
val DarkOnSurface = Color(0xFFDEE3E4)
val DarkSurfaceVariant = BrandSurface7
val DarkOnSurfaceVariant = Color(0xFFBFC8CA)
val DarkOutline = Color(0xFF899294)
