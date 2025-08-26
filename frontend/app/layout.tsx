import type { Metadata } from 'next';
import './globals.css';
export const metadata: Metadata = {
    title: 'Icon Pack Generator',
    description: 'Generate custom icon packs using AI',
};
export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
    return (
        <html lang="en" data-oid="-0-7qyp">
            <body className="" data-oid="2s1vt87">
                {children}
            </body>
        </html>
    );
}
