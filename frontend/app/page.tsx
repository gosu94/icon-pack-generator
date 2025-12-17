"use client";

import { useState } from "react";
import Navigation from "../components/Navigation";
import Footer from "../components/Footer";
import Image from "next/image";
import { Download, Rocket, Sparkles, Zap, Palette, Monitor, Layers, ChevronRight, Play, CheckCircle2, Tag, PenTool, Home, User, Settings } from "lucide-react";
import { useAuth } from "@/context/AuthContext";
import LoginModal from "../components/LoginModal";

export default function LandingPage() {
    const { authState } = useAuth();
    const [isLoginModalOpen, setIsLoginModalOpen] = useState(false);
    const [isModalVisible, setIsModalVisible] = useState(false);
    const [selectedImage, setSelectedImage] = useState<string | null>(null);

    const openLoginModal = () => {
        setIsLoginModalOpen(true);
        setTimeout(() => setIsModalVisible(true), 10);
    };

    const closeLoginModal = () => {
        setIsModalVisible(false);
        setTimeout(() => setIsLoginModalOpen(false), 300);
    };

    const openImageModal = (image: string) => {
        setSelectedImage(image);
    };

    const closeImageModal = () => {
        setSelectedImage(null);
    };

    const handleStartCreating = () => {
        if (authState.authenticated) {
            window.location.href = "/dashboard/index.html";
        } else {
            openLoginModal();
        }
    };

    const handleLoginSuccess = () => {
        window.location.href = "/dashboard/index.html";
    };

    // Gallery Data
    const galleryImagesRow1 = [
        "/images/gallery/icon1.webp", "/images/gallery/icon2.webp", "/images/gallery/icon3.webp",
        "/images/gallery/icon10.webp", "/images/gallery/icon11.webp", "/images/gallery/icon12.webp",
        "/images/gallery/icon19.webp", "/images/gallery/icon20.webp", "/images/gallery/icon21.webp",
        "/images/gallery/icon28.webp", "/images/gallery/icon29.webp", "/images/gallery/icon30.webp",
    ];

    const galleryImagesRow2 = [
        "/images/gallery/icon4.webp", "/images/gallery/icon5.webp", "/images/gallery/icon6.webp",
        "/images/gallery/icon13.webp", "/images/gallery/icon14.webp", "/images/gallery/icon15.webp",
        "/images/gallery/icon22.webp", "/images/gallery/icon23.webp", "/images/gallery/icon24.webp",
        "/images/gallery/icon31.webp", "/images/gallery/icon32.webp", "/images/gallery/icon33.webp",
    ];

    const galleryImagesRow3 = [
        "/images/gallery/icon7.webp", "/images/gallery/icon8.webp", "/images/gallery/icon9.webp",
        "/images/gallery/icon16.webp", "/images/gallery/icon17.webp", "/images/gallery/icon18.webp",
        "/images/gallery/icon25.webp", "/images/gallery/icon26.webp", "/images/gallery/icon27.webp",
        "/images/gallery/icon34.webp", "/images/gallery/icon35.webp", "/images/gallery/icon36.webp",
    ];

    const illustrationImages = [
        "/images/illustrations/illustration1.webp", "/images/illustrations/illustration2.webp",
        "/images/illustrations/illustration3.webp", "/images/illustrations/illustration4.webp",
        "/images/illustrations/illustration5.webp", "/images/illustrations/illustration6.webp",
        "/images/illustrations/illustration7.webp", "/images/illustrations/illustration8.webp",
        "/images/illustrations/illustration9.webp", "/images/illustrations/illustration10.webp",
        "/images/illustrations/illustration11.webp", "/images/illustrations/illustration12.webp",
        "/images/illustrations/illustration13.webp", "/images/illustrations/illustration14.webp",
        "/images/illustrations/illustration15.webp", "/images/illustrations/illustration16.webp",
    ];

    return (
        <div className="min-h-screen bg-slate-50 text-slate-900 font-sans selection:bg-purple-100 selection:text-purple-900">
            <Navigation />

            {/* Hero Section */}
            <section className="relative pt-32 pb-20 lg:pt-40 lg:pb-32 overflow-hidden">
                {/* Background Layers */}
                <div className="absolute inset-0 overflow-hidden pointer-events-none select-none">
                    {/* Layer 1: Gradient Blobs */}
                    <div className="absolute -inset-[10%] opacity-40 z-0">
                         <div className="absolute top-0 left-1/4 w-[500px] h-[500px] bg-blue-400/30 rounded-full mix-blend-multiply filter blur-[80px] animate-blob"></div>
                         <div className="absolute top-0 right-1/4 w-[500px] h-[500px] bg-purple-400/30 rounded-full mix-blend-multiply filter blur-[80px] animate-blob animation-delay-2000"></div>
                         <div className="absolute -bottom-32 left-1/3 w-[500px] h-[500px] bg-indigo-400/30 rounded-full mix-blend-multiply filter blur-[80px] animate-blob animation-delay-4000"></div>
                    </div>

                    {/* Layer 2: Icon Marquee */}
                    <div className="absolute inset-0 z-0 opacity-50 flex flex-col justify-center gap-10 scale-110 origin-center">
                         <div className="flex w-max animate-scroll-left-to-right-fast">
                            {[...galleryImagesRow1, ...galleryImagesRow1, ...galleryImagesRow1, ...galleryImagesRow1].map((image, index) => (
                                <div key={`hero-row1-${index}`} className="mx-4 w-24 h-24 md:w-32 md:h-32 bg-white rounded-2xl shadow-sm border border-slate-200/60 flex items-center justify-center p-4">
                                    <Image src={image} alt="" width={128} height={128} className="w-full h-full object-contain transition-all" />
                                </div>
                            ))}
                        </div>
                        <div className="flex w-max animate-scroll-left-to-right">
                            {[...galleryImagesRow2, ...galleryImagesRow2, ...galleryImagesRow2, ...galleryImagesRow2].map((image, index) => (
                                <div key={`hero-row2-${index}`} className="mx-4 w-24 h-24 md:w-32 md:h-32 bg-white rounded-2xl shadow-sm border border-slate-200/60 flex items-center justify-center p-4">
                                    <Image src={image} alt="" width={128} height={128} className="w-full h-full object-contain transition-all" />
                                </div>
                            ))}
                        </div>
                        <div className="flex w-max animate-scroll-left-to-right-slow">
                            {[...galleryImagesRow3, ...galleryImagesRow3, ...galleryImagesRow3, ...galleryImagesRow3].map((image, index) => (
                                <div key={`hero-row3-${index}`} className="mx-4 w-24 h-24 md:w-32 md:h-32 bg-white rounded-2xl shadow-sm border border-slate-200/60 flex items-center justify-center p-4">
                                    <Image src={image} alt="" width={128} height={128} className="w-full h-full object-contain transition-all" />
                                </div>
                            ))}
                        </div>
                        {/* Extra row to ensure coverage */}
                        <div className="flex w-max animate-scroll-left-to-right-fast">
                            {[...galleryImagesRow2, ...galleryImagesRow2, ...galleryImagesRow2, ...galleryImagesRow2].map((image, index) => (
                                <div key={`hero-row4-${index}`} className="mx-4 w-24 h-24 md:w-32 md:h-32 bg-white rounded-2xl shadow-sm border border-slate-200/60 flex items-center justify-center p-4">
                                    <Image src={image} alt="" width={128} height={128} className="w-full h-full object-contain transition-all" />
                                </div>
                            ))}
                        </div>
                    </div>

                    {/* Layer 3: Fade Overlay */}
                    <div className="absolute inset-0 bg-gradient-to-b from-slate-50/40 via-slate-50/70 to-slate-50 z-10"></div>
                </div>

                <div className="container mx-auto px-6 relative z-20">
                    <div className="flex flex-col items-center text-center max-w-4xl mx-auto">
                        <div className="inline-flex items-center space-x-2 bg-white/50 backdrop-blur-sm border border-slate-200/50 rounded-full px-4 py-1.5 mb-8 shadow-sm hover:shadow-md transition-all duration-300">
                            <Sparkles className="w-4 h-4 text-purple-600" />
                            <span className="text-sm font-medium text-slate-600">
                                AI-Powered Icon Generation
                            </span>
                        </div>

                        <h1 className="text-5xl md:text-7xl font-bold tracking-tight mb-6 bg-clip-text text-transparent bg-gradient-to-r from-slate-900 via-slate-800 to-slate-600">
                            Create Stunning <span className="text-transparent bg-clip-text bg-gradient-to-r from-blue-600 to-purple-600">Icon Packs</span> in Seconds
                        </h1>

                        <p className="text-lg md:text-xl text-slate-600 mb-10 max-w-2xl leading-relaxed">
                            Transform your ideas into professional, consistent icon sets using cutting-edge AI.
                            Export vector-ready assets for your next big project.
                        </p>

                        <div className="flex flex-col sm:flex-row gap-4 w-full sm:w-auto">
                            <button
                                onClick={handleStartCreating}
                                className="group relative inline-flex items-center justify-center px-8 py-4 text-lg font-semibold text-white transition-all duration-200 bg-gradient-to-r from-blue-600 to-purple-600 rounded-full hover:from-blue-700 hover:to-purple-700 shadow-lg hover:shadow-purple-500/25 ring-offset-2 focus:ring-2 focus:ring-purple-600"
                            >
                                Start Creating Free
                                <ChevronRight className="w-5 h-5 ml-2 group-hover:translate-x-1 transition-transform" />
                            </button>
                        </div>
                    </div>

                    {/* Hero Video / Visual */}
                    <div id="demo-video" className="mt-20 relative max-w-5xl mx-auto">
                        <div className="relative rounded-2xl overflow-hidden shadow-2xl border border-slate-200/50 bg-slate-900/5 aspect-video">
                            <div className="absolute inset-0 bg-gradient-to-tr from-purple-500/10 to-blue-500/10 z-10 pointer-events-none"></div>
                            <iframe
                                src="https://player.vimeo.com/video/1138289366?badge=0&amp;autopause=0&amp;player_id=0&amp;app_id=58479"
                                allow="autoplay; fullscreen; picture-in-picture; clipboard-write"
                                className="w-full h-full"
                                title="Icon Pack Generator Promo Video"
                            ></iframe>
                        </div>
                        {/* Decorative elements behind video */}
                        <div className="absolute -top-10 -right-10 w-32 h-32 bg-gradient-to-br from-blue-400 to-purple-400 rounded-full blur-3xl opacity-20 -z-10"></div>
                        <div className="absolute -bottom-10 -left-10 w-40 h-40 bg-gradient-to-br from-purple-400 to-pink-400 rounded-full blur-3xl opacity-20 -z-10"></div>
                    </div>
                </div>
            </section>

            {/* Core Features - Bento Grid Style */}
            <section className="py-24 bg-white">
                <div className="container mx-auto px-6">
                    <div className="text-center mb-16">
                        <h2 className="text-3xl md:text-4xl font-bold text-slate-900 mb-4">Everything You Need</h2>
                        <p className="text-slate-600 text-lg max-w-2xl mx-auto">
                            Built for designers, developers, and creators who value quality and efficiency.
                        </p>
                    </div>

                    <div className="grid grid-cols-1 md:grid-cols-3 gap-6 max-w-6xl mx-auto">
                        {/* Large Card - Consistency */}
                        <div className="md:col-span-2 bg-gradient-to-br from-slate-50 to-slate-100 rounded-3xl p-8 border border-slate-200 hover:border-purple-200 transition-all group">
                            <div className="w-12 h-12 bg-blue-100 rounded-2xl flex items-center justify-center mb-6 group-hover:scale-110 transition-transform">
                                <Palette className="w-6 h-6 text-blue-600" />
                            </div>
                            <h3 className="text-2xl font-bold text-slate-900 mb-3">Visually Consistent Style</h3>
                            <p className="text-slate-600 leading-relaxed mb-6">
                                Our AI ensures every icon in your pack maintains the exact same visual language, line weight, and color palette. 
                                Perfect for cohesive brand identities.
                            </p>
                            <div className="h-32 bg-white rounded-xl border border-slate-100 shadow-sm relative overflow-hidden flex items-center justify-center px-4">
                                <div className="relative w-full max-w-xl h-16">
                                    {/* Layer 1: Inconsistent / Messy Icons */}
                                    <div className="absolute inset-0 flex items-center justify-between px-8 opacity-60">
                                        <div className="relative">
                                            <Home className="w-12 h-12 text-slate-400" strokeWidth={0.75} />
                                            <div className="absolute -top-1 -right-1 w-2 h-2 bg-red-400 rounded-full animate-pulse"></div>
                                        </div>
                                        <div className="relative">
                                            <User className="w-12 h-12 text-slate-500" strokeWidth={2.5} />
                                            <div className="absolute -bottom-1 -right-1 w-2 h-2 bg-red-400 rounded-full animate-pulse delay-100"></div>
                                        </div>
                                        <div className="relative">
                                            <Settings className="w-12 h-12 text-slate-400" strokeWidth={1} />
                                            <div className="absolute -top-1 -left-1 w-2 h-2 bg-red-400 rounded-full animate-pulse delay-200"></div>
                                        </div>
                                    </div>

                                    {/* Layer 2: Consistent / Clean Icons (Revealed) */}
                                    <div className="absolute inset-0 flex items-center justify-between px-8 text-blue-600 animate-reveal-consistency bg-gradient-to-r from-white/95 via-white/80 to-transparent backdrop-blur-[1px]">
                                        <Home className="w-12 h-12 drop-shadow-sm" strokeWidth={1.5} />
                                        <User className="w-12 h-12 drop-shadow-sm" strokeWidth={1.5} />
                                        <Settings className="w-12 h-12 drop-shadow-sm" strokeWidth={1.5} />
                                    </div>
                                </div>
                            </div>
                        </div>

                        {/* Tall Card - Lightning Fast */}
                        <div className="md:row-span-2 bg-gradient-to-br from-purple-900 to-slate-900 rounded-3xl p-8 text-white flex flex-col relative overflow-hidden group">
                             <div className="absolute top-0 right-0 w-64 h-64 bg-purple-500/20 rounded-full blur-3xl -translate-y-1/2 translate-x-1/2 group-hover:bg-purple-500/30 transition-colors"></div>
                             <div className="w-12 h-12 bg-white/10 backdrop-blur-sm rounded-2xl flex items-center justify-center mb-6 group-hover:rotate-12 transition-transform">
                                <Zap className="w-6 h-6 text-yellow-300" />
                            </div>
                            <h3 className="text-2xl font-bold mb-3">Lightning Fast</h3>
                            <p className="text-slate-300 leading-relaxed mb-auto">
                                Generate complete icon packs in under two minutes. Stop wasting hours searching for matching assets.
                            </p>
                            <div className="mt-8 bg-white/5 rounded-xl p-4 backdrop-blur-sm border border-white/10">
                                <div className="flex items-center justify-between text-sm mb-2">
                                    <span className="text-slate-300">Generation Speed</span>
                                    <span className="text-green-400 font-mono">&lt; 120s</span>
                                </div>
                                <div className="w-full h-2 bg-white/10 rounded-full overflow-hidden">
                                    <div className="h-full bg-gradient-to-r from-green-400 to-emerald-500 w-11/12 rounded-full"></div>
                                </div>
                            </div>
                        </div>

                        {/* Standard Card - Export */}
                        <div className="bg-white rounded-3xl p-8 border border-slate-200 hover:shadow-lg transition-all group">
                            <div className="w-12 h-12 bg-green-100 rounded-2xl flex items-center justify-center mb-6">
                                <Download className="w-6 h-6 text-green-600" />
                            </div>
                            <h3 className="text-xl font-bold text-slate-900 mb-3">Export Ready</h3>
                            <p className="text-slate-600 text-sm">
                                PNG, WebP, ICO, and fully editable SVGs. Ready for web, mobile, and print.
                            </p>
                        </div>

                        {/* Standard Card - Free Trial */}
                        <div className="bg-white rounded-3xl p-8 border border-slate-200 hover:shadow-lg transition-all group relative overflow-hidden">
                            <div className="absolute top-4 right-4 bg-green-100 text-green-700 text-xs font-bold px-2 py-1 rounded-full">FREE</div>
                             <div className="w-12 h-12 bg-orange-100 rounded-2xl flex items-center justify-center mb-6">
                                <Sparkles className="w-6 h-6 text-orange-600" />
                            </div>
                            <h3 className="text-xl font-bold text-slate-900 mb-3">Try for Free</h3>
                            <p className="text-slate-600 text-sm">
                                Get started with a free trial coin. Generate 5 professional icons at no cost.
                            </p>
                        </div>
                    </div>
                </div>
            </section>

            {/* Feature Deep Dives - Alternating Layout */}
            <section className="py-24 bg-slate-50/80 overflow-hidden">
                <div className="container mx-auto px-6 space-y-24">
                    
                    {/* Feature 1: SVG Exports */}
                    <div className="flex flex-col lg:flex-row items-center gap-12 lg:gap-20">
                        <div className="lg:w-1/2 order-2 lg:order-1">
                            <div className="relative group cursor-pointer" onClick={() => openImageModal('/images/features/feature_svg-exports.webp')}>
                                <div className="absolute inset-0 bg-gradient-to-r from-blue-600 to-purple-600 rounded-2xl blur opacity-20 group-hover:opacity-30 transition-opacity"></div>
                                <Image 
                                    src="/images/features/feature_svg-exports.webp" 
                                    alt="SVG Exports" 
                                    width={600} 
                                    height={400} 
                                    className="relative rounded-2xl shadow-xl border border-white/50 w-full"
                                />
                            </div>
                        </div>
                        <div className="lg:w-1/2 order-1 lg:order-2">
                            <div className="bg-blue-100 w-12 h-12 rounded-xl flex items-center justify-center mb-6">
                                <Monitor className="w-6 h-6 text-blue-600" />
                            </div>
                            <h3 className="text-3xl font-bold text-slate-900 mb-4">Vector-Perfect SVG Exports</h3>
                            <p className="text-lg text-slate-600 leading-relaxed mb-6">
                                Say goodbye to pixelated icons. Our AI generates refined vector paths that scale infinitely. 
                                Perfect for high-DPI displays, responsive web design, and large-format printing.
                            </p>
                            <ul className="space-y-3">
                                {['Crisp at any resolution', 'Editable paths', 'Optimized file size'].map((item, i) => (
                                    <li key={i} className="flex items-center text-slate-700">
                                        <CheckCircle2 className="w-5 h-5 text-blue-500 mr-3" />
                                        {item}
                                    </li>
                                ))}
                            </ul>
                        </div>
                    </div>

                    {/* Feature 2: Variations */}
                    <div className="flex flex-col lg:flex-row items-center gap-12 lg:gap-20">
                        <div className="lg:w-1/2">
                            <div className="bg-slate-100 w-12 h-12 rounded-xl flex items-center justify-center mb-6">
                                <Layers className="w-6 h-6 text-slate-600" />
                            </div>
                            <h3 className="text-3xl font-bold text-slate-900 mb-4">Model Variations</h3>
                            <p className="text-lg text-slate-600 leading-relaxed mb-6">
                                Switch on our latest state-of-the-art model to explore fresh variations of every icon while you generate them.
                                Prefer the original minimalist style? The classic model still powers the base generation path so you always keep that clean look.
                            </p>
                            <ul className="space-y-3">
                                {['Explore new compositions instantly', 'Blend cutting-edge + classic workflows', 'Keep brand consistency while experimenting'].map((item, i) => (
                                    <li key={i} className="flex items-center text-slate-700">
                                        <CheckCircle2 className="w-5 h-5 text-purple-500 mr-3" />
                                        {item}
                                    </li>
                                ))}
                            </ul>
                        </div>
                        <div className="lg:w-1/2">
                            <div className="relative group cursor-pointer" onClick={() => openImageModal('/images/features/feature_variations.webp')}>
                                <div className="absolute inset-0 bg-gradient-to-r from-slate-600 to-purple-600 rounded-2xl blur opacity-20 group-hover:opacity-30 transition-opacity"></div>
                                <Image
                                    src="/images/features/feature_variations.webp"
                                    alt="Icon Variations"
                                    width={600}
                                    height={400}
                                    className="relative rounded-2xl shadow-xl border border-white/50 w-full"
                                />
                            </div>
                        </div>
                    </div>

                    {/* Feature 3: Illustrations (Moved up) */}
                    <div className="flex flex-col lg:flex-row items-center gap-12 lg:gap-20">
                        <div className="lg:w-1/2 order-2 lg:order-1">
                            <div className="relative group cursor-pointer" onClick={() => openImageModal('/images/features/feature_illustrations.webp')}>
                                <div className="absolute inset-0 bg-gradient-to-r from-pink-500 to-rose-500 rounded-2xl blur opacity-20 group-hover:opacity-30 transition-opacity"></div>
                                <Image 
                                    src="/images/features/feature_illustrations.webp" 
                                    alt="Illustrations" 
                                    width={600} 
                                    height={400} 
                                    className="relative rounded-2xl shadow-xl border border-white/50 w-full"
                                />
                            </div>
                        </div>
                        <div className="lg:w-1/2 order-1 lg:order-2">
                            <div className="bg-pink-100 w-12 h-12 rounded-xl flex items-center justify-center mb-6">
                                <PenTool className="w-6 h-6 text-pink-600" />
                            </div>
                            <h3 className="text-3xl font-bold text-slate-900 mb-4">Cohesive Illustrations</h3>
                            <p className="text-lg text-slate-600 leading-relaxed mb-6">
                                Extend your visual system beyond icons. Generate cohesive, on-brand illustrations that 
                                complement your icons and keep a consistent visual tone across your entire project.
                            </p>
                        </div>
                    </div>

                    {/* Feature 4: GIFs */}
                    <div className="flex flex-col lg:flex-row items-center gap-12 lg:gap-20">
                        <div className="lg:w-1/2">
                            <div className="bg-purple-100 w-12 h-12 rounded-xl flex items-center justify-center mb-6">
                                <Play className="w-6 h-6 text-purple-600" />
                            </div>
                            <h3 className="text-3xl font-bold text-slate-900 mb-4">Animated GIF Icons</h3>
                            <p className="text-lg text-slate-600 leading-relaxed mb-6">
                                Bring your interface to life with motion. Generate seamless looping animations that retain 
                                your specific style. Great for loading states, micro-interactions, and social media.
                            </p>
                        </div>
                        <div className="lg:w-1/2">
                            <div className="relative group cursor-pointer" onClick={() => openImageModal('/images/features/feature_gifs.gif')}>
                                <div className="absolute inset-0 bg-gradient-to-l from-purple-600 to-pink-600 rounded-2xl blur opacity-20 group-hover:opacity-30 transition-opacity"></div>
                                <Image 
                                    src="/images/features/feature_gifs.gif" 
                                    alt="GIF Icons" 
                                    width={600} 
                                    height={400} 
                                    unoptimized
                                    className="relative rounded-2xl shadow-xl border border-white/50 w-full"
                                />
                            </div>
                        </div>
                    </div>

                    {/* Feature 5: Labels */}
                    <div className="flex flex-col lg:flex-row items-center gap-12 lg:gap-20">
                        <div className="lg:w-1/2 order-2 lg:order-1">
                            <div className="relative group cursor-pointer" onClick={() => openImageModal('/images/features/feature_labels.webp')}>
                                <div className="absolute inset-0 bg-gradient-to-r from-indigo-500 to-blue-500 rounded-2xl blur opacity-20 group-hover:opacity-30 transition-opacity"></div>
                                <Image 
                                    src="/images/features/feature_labels.webp" 
                                    alt="Custom Labels" 
                                    width={600} 
                                    height={400} 
                                    className="relative rounded-2xl shadow-xl border border-white/50 w-full"
                                />
                            </div>
                        </div>
                        <div className="lg:w-1/2 order-1 lg:order-2">
                            <div className="bg-indigo-100 w-12 h-12 rounded-xl flex items-center justify-center mb-6">
                                <Tag className="w-6 h-6 text-indigo-600" />
                            </div>
                            <h3 className="text-3xl font-bold text-slate-900 mb-4">Custom Labels</h3>
                            <p className="text-lg text-slate-600 leading-relaxed mb-6">
                                Add text that matches your design language. Create custom labels that blend seamlessly 
                                with your icon style — whether you start from scratch or describe what you need in plain text.
                            </p>
                        </div>
                    </div>

                     {/* Feature 6: Mockups */}
                     <div className="flex flex-col lg:flex-row items-center gap-12 lg:gap-20">
                        <div className="lg:w-1/2">
                            <div className="bg-emerald-100 w-12 h-12 rounded-xl flex items-center justify-center mb-6">
                                <Layers className="w-6 h-6 text-emerald-600" />
                            </div>
                            <h3 className="text-3xl font-bold text-slate-900 mb-4">Instant UI Mockups</h3>
                            <p className="text-lg text-slate-600 leading-relaxed mb-6">
                                Visualize your icons in context immediately. Generate full UI mockups that match your 
                                icon pack's aesthetic, helping you present your work better - or flip the process: start with a mockup and use it as the foundation for your icon set.
                            </p>
                        </div>
                        <div className="lg:w-1/2">
                            <div className="relative group cursor-pointer" onClick={() => openImageModal('/images/features/feature_mockups.webp')}>
                                <div className="absolute inset-0 bg-gradient-to-r from-emerald-500 to-teal-500 rounded-2xl blur opacity-20 group-hover:opacity-30 transition-opacity"></div>
                                <Image 
                                    src="/images/features/feature_mockups.webp" 
                                    alt="UI Mockups" 
                                    width={600} 
                                    height={400} 
                                    className="relative rounded-2xl shadow-xl border border-white/50 w-full"
                                />
                            </div>
                        </div>
                     </div>

                </div>
            </section>

             {/* Illustrations Gallery Section */}
             <section className="py-24 bg-white">
                <div className="container mx-auto px-6">
                    <div className="flex flex-col md:flex-row justify-between items-end mb-12 gap-6">
                        <div>
                            <h2 className="text-3xl md:text-4xl font-bold text-slate-900 mb-4">Beautiful Illustrations</h2>
                            <p className="text-slate-600 text-lg max-w-xl">
                                Need more than just icons? Generate full-scene illustrations in the same coherent style.
                            </p>
                        </div>
                        <button 
                            onClick={handleStartCreating}
                            className="text-purple-600 font-semibold hover:text-purple-700 flex items-center"
                        >
                            Start Generating Illustrations <ChevronRight className="w-4 h-4 ml-1" />
                        </button>
                    </div>

                    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6">
                        {illustrationImages.map((image, index) => (
                            <div key={index} 
                                onClick={() => openImageModal(image)}
                                className="group relative rounded-2xl overflow-hidden aspect-[4/3] cursor-pointer shadow-md hover:shadow-xl transition-all hover:-translate-y-1"
                            >
                                <Image src={image} alt={`Illustration ${index}`} fill className="object-cover transition-transform duration-500 group-hover:scale-110" />
                                <div className="absolute inset-0 bg-gradient-to-t from-black/60 via-transparent to-transparent opacity-0 group-hover:opacity-100 transition-opacity flex items-end p-6">
                                    <span className="text-white font-medium">View Illustration</span>
                                </div>
                            </div>
                        ))}
                    </div>
                </div>
             </section>

            {/* CTA Section */}
            <section className="py-20 px-6">
                <div className="max-w-5xl mx-auto">
                    <div className="relative bg-slate-900 rounded-[2.5rem] p-12 md:p-20 text-center overflow-hidden shadow-2xl">
                        {/* Background Glows */}
                        <div className="absolute top-0 left-0 w-full h-full overflow-hidden pointer-events-none">
                            <div className="absolute top-0 left-1/4 w-96 h-96 bg-purple-600/30 rounded-full blur-[100px]"></div>
                            <div className="absolute bottom-0 right-1/4 w-96 h-96 bg-blue-600/30 rounded-full blur-[100px]"></div>
                        </div>

                        <div className="relative z-10">
                            <h2 className="text-4xl md:text-5xl font-bold text-white mb-6 tracking-tight">
                                Ready to elevate your design?
                            </h2>
                            <p className="text-slate-300 text-lg md:text-xl mb-10 max-w-2xl mx-auto">
                                Join thousands of creators saving time and building better products with AI-generated assets.
                            </p>
                            <button
                                onClick={handleStartCreating}
                                className="bg-white text-slate-900 hover:bg-slate-50 font-bold py-4 px-10 rounded-full shadow-[0_0_20px_rgba(255,255,255,0.3)] hover:shadow-[0_0_30px_rgba(255,255,255,0.4)] transform transition-all hover:scale-105 text-lg"
                            >
                                Start Generating Now
                            </button>
                            <p className="mt-6 text-slate-400 text-sm">No credit card required for free trial.</p>
                        </div>
                    </div>
                </div>
            </section>

            {/* Modals */}
            <LoginModal
                isOpen={isLoginModalOpen}
                isVisible={isModalVisible}
                onClose={closeLoginModal}
                onSuccess={handleLoginSuccess}
            />

            {selectedImage && (
                <div
                    className="fixed inset-0 bg-slate-900/90 backdrop-blur-md flex items-center justify-center z-[100] p-4"
                    onClick={closeImageModal}
                >
                    <div className="relative max-w-6xl max-h-[90vh] w-full h-full flex items-center justify-center" onClick={(e) => e.stopPropagation()}>
                        <Image
                            src={selectedImage}
                            alt="Preview"
                            width={1920}
                            height={1080}
                            className="object-contain max-h-full rounded-lg shadow-2xl"
                        />
                        <button
                            onClick={closeImageModal}
                            className="absolute top-4 right-4 md:-top-12 md:-right-12 text-white/70 hover:text-white bg-black/20 hover:bg-black/40 rounded-full p-2 transition-colors"
                        >
                            <svg xmlns="http://www.w3.org/2000/svg" className="h-8 w-8" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                            </svg>
                        </button>
                    </div>
                </div>
            )}

            <Footer />
        </div>
    );
}
