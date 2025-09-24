import type { Metadata } from "next";
import "./globals.css";
import { AuthProvider } from "../context/AuthContext";

export const metadata: Metadata = {
    title: 'Icon Pack Generator',
    description: 'Generate consistent icon packs in minutes',
    openGraph: {
        title: 'Icon Pack Generator',
        description: 'Generate consistent icon packs in minutes',
        url: 'https://iconpackgen.com',
        siteName: 'Icon Pack Generator',
        images: [
            {
                url: 'https://iconpackgen.com/images/open-graph-logo.png',
                width: 1200,
                height: 630,
                alt: 'Icon Pack Generator Preview',
            },
        ],
        type: 'website',
    },
    twitter: {
        card: 'summary_large_image',
        title: 'Icon Pack Generator',
        description: 'Generate consistent icon packs in minutes',
        images: ['https://iconpackgen.com/images/open-graph-logo.png'],
    },

};

export default function RootLayout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en">
      <body>
        <AuthProvider>{children}</AuthProvider>
      </body>
    </html>
  );
}