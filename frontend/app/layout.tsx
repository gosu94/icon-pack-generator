import type { Metadata } from "next";
import "./globals.css";
export const metadata: Metadata = {
  title: "Icon Pack Generator",
  description: "Generate custom icon packs using AI",
};
export default function RootLayout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en">
      <body className="">{children}</body>
    </html>
  );
}
