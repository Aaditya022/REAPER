import React from "react"
import type { Metadata } from 'next'
import { Space_Grotesk, JetBrains_Mono, Fraunces } from 'next/font/google'
import './globals.css'

const spaceGrotesk = Space_Grotesk({ 
  subsets: ["latin"],
  weight: ["400", "500", "600", "700"],
  variable: '--font-space-grotesk'
});

const jetbrainsMono = JetBrains_Mono({ 
  subsets: ["latin"],
  variable: '--font-jetbrains'
});

const fraunces = Fraunces({
  subsets: ["latin"],
  style: ["italic"],
  weight: "variable",
  axes: ["opsz"],
  variable: '--font-fraunces'
});

export const metadata: Metadata = {
  title: 'REAPER - Build it. Ship it.',
  description: 'Interactive full-stack scaffolding with deployment built in. Generate a full-stack application and deploy it to Zerops without rebuilding the deployment workflow by hand.',
}

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode
}>) {
  return (
    <html lang="en">
      <body className={`${spaceGrotesk.variable} ${jetbrainsMono.variable} ${fraunces.variable} font-sans antialiased`}>
        {children}
      </body>
    </html>
  )
}
