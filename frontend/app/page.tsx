"use client";

import {useState} from "react";
import Navigation from "../components/Navigation";
import Footer from "../components/Footer";
import Image from "next/image";
import {Download, Rocket, Sparkles, Zap} from "lucide-react";
import {useAuth} from "@/context/AuthContext";
import LoginModal from "../components/LoginModal";

export default function LandingPage() {
    const {authState} = useAuth();
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
            // If user is authenticated, redirect to main generator page
            window.location.href = "/dashboard/index.html";
        } else {
            // If not authenticated, open login modal
            openLoginModal();
        }
    };

    const handleLoginSuccess = () => {
        // After successful login, redirect to dashboard
        window.location.href = "/dashboard/index.html";
    };

    // Sample gallery images - you can replace these with actual generated icons
    const galleryImagesRow1 = [
        "/images/gallery/icon1.webp",
        "/images/gallery/icon2.webp",
        "/images/gallery/icon3.webp",
        "/images/gallery/icon10.webp",
        "/images/gallery/icon11.webp",
        "/images/gallery/icon12.webp",
        "/images/gallery/icon19.webp",
        "/images/gallery/icon20.webp",
        "/images/gallery/icon21.webp",
        "/images/gallery/icon28.webp",
        "/images/gallery/icon29.webp",
        "/images/gallery/icon30.webp",
    ];

    const galleryImagesRow2 = [
        "/images/gallery/icon4.webp",
        "/images/gallery/icon5.webp",
        "/images/gallery/icon6.webp",
        "/images/gallery/icon13.webp",
        "/images/gallery/icon14.webp",
        "/images/gallery/icon15.webp",
        "/images/gallery/icon22.webp",
        "/images/gallery/icon23.webp",
        "/images/gallery/icon24.webp",
        "/images/gallery/icon31.webp",
        "/images/gallery/icon32.webp",
        "/images/gallery/icon33.webp",
    ];

    const galleryImagesRow3 = [
        "/images/gallery/icon7.webp",
        "/images/gallery/icon8.webp",
        "/images/gallery/icon9.webp",
        "/images/gallery/icon16.webp",
        "/images/gallery/icon17.webp",
        "/images/gallery/icon18.webp",
        "/images/gallery/icon25.webp",
        "/images/gallery/icon26.webp",
        "/images/gallery/icon27.webp",
        "/images/gallery/icon34.webp",
        "/images/gallery/icon35.webp",
        "/images/gallery/icon36.webp",
    ];

    const illustrationImages = [
        "/images/illustrations/illustration1.webp",
        "/images/illustrations/illustration2.webp",
        "/images/illustrations/illustration3.webp",
        "/images/illustrations/illustration4.webp",
        "/images/illustrations/illustration5.webp",
        "/images/illustrations/illustration6.webp",
        "/images/illustrations/illustration7.webp",
        "/images/illustrations/illustration8.webp",
    ];

    const mockupImages = [
        "/images/mockups/mockup1.webp",
        "/images/mockups/mockup2.webp",
        "/images/mockups/mockup3.webp",
        "/images/mockups/mockup4.webp",
    ];


    return (
        <div className="min-h-screen bg-gradient-to-br from-slate-50 via-white to-blue-50/30">
            <Navigation/>

            {/* Hero Section with Video Card */}
            <section className="px-6 py-12">
                <div className="max-w-7xl mx-auto">
                    <h1 className="sr-only">Generate icons with AI</h1>
                    <div
                        className="bg-white/80 backdrop-blur-md rounded-3xl shadow-2xl border border-purple-200/50 overflow-hidden">
                        <div className="flex flex-col lg:flex-row">
                            {/* Video Section - 3/4 width */}
                            <div
                                className="lg:w-3/4 relative bg-gradient-to-br from-blue-600 to-purple-600 p-6 md:p-8 flex items-center justify-center min-h-[280px] md:min-h-[400px]">
                                <div className="relative w-full h-full flex items-center justify-center">
                                    <div
                                        className="relative w-full max-w-2xl aspect-video bg-black/20 rounded-2xl flex items-center justify-center backdrop-blur-sm border border-white/20 overflow-hidden">
                                        <iframe
                                            src="https://player.vimeo.com/video/1122340216?badge=0&amp;autopause=0&amp;player_id=0&amp;app_id=58479"
                                            allow="autoplay; fullscreen; picture-in-picture; clipboard-write"
                                            className="w-full h-full"
                                            title="Icon Pack Generator Promo Video"
                                        ></iframe>
                                    </div>
                                </div>
                            </div>

                            {/* Caption Section - 1/4 width */}
                            <div
                                className="lg:w-1/4 p-6 md:p-8 flex flex-col justify-center bg-gradient-to-br from-white/50 to-purple-50/50">
                                <div className="space-y-6">
                                    <div className="flex items-center space-x-2">
                                        <Sparkles className="w-6 h-6 text-purple-600"/>
                                        <span
                                            className="text-sm font-semibold text-purple-600 uppercase tracking-wide">AI-Powered</span>
                                    </div>

                                    <h1 className="text-3xl font-bold text-slate-900 leading-tight">
                                        Create Stunning Icon Packs in Minutes
                                    </h1>

                                    <p className="text-slate-600 leading-relaxed">
                                        Transform your ideas into professional icon packs using cutting-edge AI
                                        technology. Generate, and export high-quality icons for your projects.
                                    </p>

                                    <div className="flex flex-col space-y-3">
                                        <button
                                            onClick={handleStartCreating}
                                            className="w-full bg-gradient-to-r from-blue-600 to-purple-600 hover:from-blue-700 hover:to-purple-700 text-white font-semibold py-3 px-6 rounded-xl shadow-lg hover:shadow-xl transform hover:scale-[1.02] transition-all duration-200"
                                        >
                                            Start Creating
                                        </button>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </section>

            {/* New UI Mockups Feature Section */}
            <section className="px-6 py-12">
                <div className="max-w-7xl mx-auto">
                    <div
                        className="bg-white/80 backdrop-blur-md rounded-3xl shadow-2xl border border-teal-200/50 overflow-hidden">
                        <div className="flex flex-col lg:flex-row">
                            {/* Caption Section */}
                            <div
                                className="lg:w-1/3 p-6 md:p-8 flex flex-col justify-center bg-gradient-to-br from-white/50 to-teal-50/50">
                                <div className="space-y-6">
                                    <div className="flex items-center space-x-2">
                                        <Rocket className="w-6 h-6 text-blue-600"/>
                                        <span
                                            className="text-sm font-semibold text-blue-600 uppercase tracking-wide">New Feature</span>
                                    </div>

                                    <h2 className="text-3xl font-bold text-slate-900 leading-tight">
                                        UI Mockups
                                    </h2>

                                    <p className="text-slate-600 leading-relaxed">
                                        Transform your ideas into stunning UI mockups instantly. Generate professional-quality interface designs in 16:9 aspect ratio, perfect for presentations, pitches, and prototypes. Use text descriptions or reference images to bring your vision to life.
                                    </p>

                                </div>
                            </div>
                            {/* Video Section */}
                            <div
                                className="lg:w-2/3 relative bg-gradient-to-br from-blue-600 to-pink-600 p-6 md:p-8 flex items-center justify-center min-h-[280px] md:min-h-[400px]">
                                <div className="relative w-full h-full flex items-center justify-center">
                                    <div
                                        className="relative w-full max-w-2xl aspect-video bg-black/20 rounded-2xl flex items-center justify-center backdrop-blur-sm border border-white/20 overflow-hidden">
                                        <iframe
                                            src="https://player.vimeo.com/video/1129000758?badge=0&amp;autopause=0&amp;player_id=0&amp;app_id=58479"
                                            allow="autoplay; fullscreen; picture-in-picture; clipboard-write"
                                            className="w-full h-full"
                                            title="UI Mockup Generator Feature Video"
                                        ></iframe>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </section>

            {/* New Illustration Feature Section */}
            <section className="px-6 py-12">
                <div className="max-w-7xl mx-auto">
                    <div
                        className="bg-white/80 backdrop-blur-md rounded-3xl shadow-2xl border border-pink-200/50 overflow-hidden">
                        <div className="flex flex-col lg:flex-row">
                            {/* Video Section */}
                            <div
                                className="lg:w-2/3 relative bg-gradient-to-br from-purple-600 to-pink-600 p-6 md:p-8 flex items-center justify-center min-h-[280px] md:min-h-[400px]">
                                <div className="relative w-full h-full flex items-center justify-center">
                                    <div
                                        className="relative w-full max-w-2xl aspect-video bg-black/20 rounded-2xl flex items-center justify-center backdrop-blur-sm border border-white/20 overflow-hidden">
                                        <iframe
                                            src="https://player.vimeo.com/video/1125205255?badge=0&amp;autopause=0&amp;player_id=0&amp;app_id=58479"
                                            allow="autoplay; fullscreen; picture-in-picture; clipboard-write"
                                            className="w-full h-full"
                                            title="Illustration Generator Feature Video"
                                        ></iframe>
                                    </div>
                                </div>
                            </div>
                            {/* Caption Section */}
                            <div
                                className="lg:w-1/3 p-6 md:p-8 flex flex-col justify-center bg-gradient-to-br from-white/50 to-pink-50/50">
                                <div className="space-y-6">
                                    <div className="flex items-center space-x-2">
                                        <Sparkles className="w-6 h-6 text-pink-600"/>
                                        <span
                                            className="text-sm font-semibold text-pink-600 uppercase tracking-wide">New Feature</span>
                                    </div>

                                    <h2 className="text-3xl font-bold text-slate-900 leading-tight">
                                        Illustrations
                                    </h2>

                                    <p className="text-slate-600 leading-relaxed">
                                        Unleash your creativity with our new illustration generator. Create visually consistent, high-quality illustrations for your marketing materials, websites, and applications. Describe your vision and let our AI bring it to life.
                                    </p>

                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </section>

            {/* Features Section */}
            <section className="px-6 py-16">
                <div className="max-w-7xl mx-auto">
                    <div className="text-center mb-12">
                        <h2 className="text-4xl font-bold text-slate-900 mb-4">
                            Powerful Features for Icon Creation
                        </h2>
                        <p className="text-xl text-slate-600 max-w-3xl mx-auto">
                            Everything you need to create professional icon packs with AI assistance
                        </p>
                    </div>

                    <div className="grid md:grid-cols-2 lg:grid-cols-3 gap-8">

                        {/* Feature 5 */}
                        <div
                            className="bg-white/80 backdrop-blur-md rounded-2xl p-8 shadow-lg border border-purple-200/30 hover:shadow-xl transition-all duration-300 hover:scale-[1.02]">
                            <div
                                className="w-12 h-12 bg-gradient-to-br from-blue-500 to-indigo-500 rounded-xl flex items-center justify-center mb-6">
                                <svg className="w-6 h-6 text-white" fill="none" stroke="currentColor"
                                     viewBox="0 0 24 24">
                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                                          d="M4 5a1 1 0 011-1h14a1 1 0 011 1v2a1 1 0 01-1 1H5a1 1 0 01-1-1V5zM4 13a1 1 0 011-1h6a1 1 0 011 1v6a1 1 0 01-1 1H5a1 1 0 01-1-1v-6zM16 13a1 1 0 011-1h2a1 1 0 011 1v6a1 1 0 01-1 1h-2a1 1 0 01-1-1v-6z"/>
                                </svg>
                            </div>
                            <h3 className="text-xl font-bold text-slate-900 mb-4">Visually Consistent Style</h3>
                            <p className="text-slate-600 leading-relaxed">
                                All icons in your pack maintain the same visual style, color palette, and design
                                language. Perfect cohesion for professional applications and brand consistency.
                            </p>
                        </div>

                        {/* Feature 2 */}
                        <div
                            className="bg-white/80 backdrop-blur-md rounded-2xl p-8 shadow-lg border border-green-200/50 hover:shadow-xl transition-all duration-300 hover:scale-[1.02] relative overflow-hidden">
                            {/* Free badge */}
                            <div
                                className="absolute top-4 right-4 bg-gradient-to-r from-green-500 to-emerald-500 text-white text-xs font-bold px-2 py-1 rounded-full">
                                FREE
                            </div>
                            <div
                                className="w-12 h-12 bg-gradient-to-br from-green-500 to-emerald-500 rounded-xl flex items-center justify-center mb-6">
                                <div className="w-6 h-6 bg-white rounded-full flex items-center justify-center">
                                    <span className="text-green-600 text-xs font-bold">T</span>
                                </div>
                            </div>
                            <h3 className="text-xl font-bold text-slate-900 mb-4">Try for Free</h3>
                            <p className="text-slate-600 leading-relaxed">
                                Get started instantly with a free trial coin! Generate 5 professional icons or 2 illustrations at no cost
                                and experience our AI-powered creation process.
                            </p>
                            <div className="mt-4 flex items-center space-x-2 text-sm text-green-600 font-semibold">
                                <div className="w-4 h-4 bg-green-500 rounded-full flex items-center justify-center">
                                    <span className="text-white text-xs font-bold">T</span>
                                </div>
                                <span>1 Trial Coin = 5 Icons (out of 9) / 2 illustrations (out of 4) / 1 UI mockup (out of 2)</span>
                            </div>
                        </div>

                        {/* Feature 3 */}
                        <div
                            className="bg-white/80 backdrop-blur-md rounded-2xl p-8 shadow-lg border border-purple-200/30 hover:shadow-xl transition-all duration-300 hover:scale-[1.02]">
                            <div
                                className="w-12 h-12 bg-gradient-to-br from-green-500 to-blue-500 rounded-xl flex items-center justify-center mb-6">
                                <Download className="w-6 h-6 text-white"/>
                            </div>
                            <h3 className="text-xl font-bold text-slate-900 mb-4">Export Ready Files</h3>
                            <div className="text-slate-600 leading-relaxed">
                                <p className="mb-2">Download your icons in multiple formats and sizes:</p>
                                <ul className="list-disc list-inside space-y-1 text-sm">
                                    <li><span className="font-semibold">SVG</span> AI-vectorized</li>
                                    <li><span className="font-semibold">PNG:</span> 32, 64, 128, 256, 512px</li>
                                    <li><span className="font-semibold">WebP:</span> 32, 64, 128, 256, 512px</li>
                                    <li><span className="font-semibold">ICO:</span> 16, 32, 48, 64, 128, 256px</li>
                                </ul>
                            </div>
                        </div>

                        {/* Feature 4 */}
                        <div
                            className="bg-white/80 backdrop-blur-md rounded-2xl p-8 shadow-lg border border-purple-200/30 hover:shadow-xl transition-all duration-300 hover:scale-[1.02]">
                            <div
                                className="w-12 h-12 bg-gradient-to-br from-orange-500 to-red-500 rounded-xl flex items-center justify-center mb-6">
                                <Sparkles className="w-6 h-6 text-white"/>
                            </div>
                            <h3 className="text-xl font-bold text-slate-900 mb-4">Style Variations</h3>
                            <p className="text-slate-600 leading-relaxed">
                                Use the 'Additional Variation' option to enrich your set with an alternate style.
                                Get more choices and find the perfect look for your project.
                            </p>
                        </div>

                        {/* Feature 1 */}
                        <div
                            className="bg-white/80 backdrop-blur-md rounded-2xl p-8 shadow-lg border border-purple-200/30 hover:shadow-xl transition-all duration-300 hover:scale-[1.02]">
                            <div
                                className="w-12 h-12 bg-gradient-to-br from-blue-500 to-purple-500 rounded-xl flex items-center justify-center mb-6">
                                <Zap className="w-6 h-6 text-white"/>
                            </div>
                            <h3 className="text-xl font-bold text-slate-900 mb-4">Lightning Fast Generation</h3>
                            <p className="text-slate-600 leading-relaxed">
                                Generate complete icon packs in under two minutes using our advanced AI models. No more
                                waiting hours for custom designs.
                            </p>
                        </div>

                        {/* Feature 6 */}
                        <div
                            className="bg-white/80 backdrop-blur-md rounded-2xl p-8 shadow-lg border border-purple-200/30 hover:shadow-xl transition-all duration-300 hover:scale-[1.02]">
                            <div
                                className="w-12 h-12 bg-gradient-to-br from-indigo-500 to-purple-500 rounded-xl flex items-center justify-center mb-6">
                                <Image src="/images/coin.webp" alt="Coins" width={24} height={24}/>
                            </div>
                            <h3 className="text-xl font-bold text-slate-900 mb-4">Flexible Pricing</h3>
                            <p className="text-slate-600 leading-relaxed">
                                Pay only for what you use with our coin-based system. No monthly subscriptions or hidden
                                fees.
                            </p>
                        </div>
                    </div>
                </div>
            </section>

            {/* Gallery Section */}
            <section className="px-6 py-16 bg-gradient-to-br from-purple-50/50 to-blue-50/50 overflow-hidden">
                <div className="max-w-7xl mx-auto">
                    <div className="text-center mb-12">
                        <h2 className="text-4xl font-bold text-slate-900 mb-4">
                            Generated Icon Gallery
                        </h2>
                        <p className="text-xl text-slate-600 max-w-3xl mx-auto">
                            Explore stunning icons created by our AI models. Each one unique and ready for your
                            projects.
                        </p>
                    </div>

                    <div className="relative flex flex-col gap-6">
                        <div className="flex w-max animate-scroll-left-to-right-fast">
                            {[...galleryImagesRow1, ...galleryImagesRow1].map((image, index) => (
                                <div key={index}
                                     className="group relative bg-white/80 backdrop-blur-md rounded-2xl p-6 shadow-lg border border-purple-200/30 hover:shadow-xl transition-all duration-300 hover:scale-[1.05] cursor-pointer mx-3">
                                    <div
                                        className="relative aspect-square w-32 h-32 bg-gradient-to-br from-slate-100 to-slate-200 rounded-xl flex items-center justify-center overflow-hidden">
                                        <Image src={image} alt={`Gallery icon ${index + 1}`} layout="fill"
                                               className="object-cover"/>
                                    </div>
                                    <div
                                        className="absolute inset-0 bg-gradient-to-br from-blue-600/0 to-purple-600/0 group-hover:from-blue-600/10 group-hover:to-purple-600/10 rounded-2xl transition-all duration-300"></div>
                                </div>
                            ))}
                        </div>
                        <div className="flex w-max animate-scroll-left-to-right">
                            {[...galleryImagesRow2, ...galleryImagesRow2].map((image, index) => (
                                <div key={index}
                                     className="group relative bg-white/80 backdrop-blur-md rounded-2xl p-6 shadow-lg border border-purple-200/30 hover:shadow-xl transition-all duration-300 hover:scale-[1.05] cursor-pointer mx-3">
                                    <div
                                        className="relative aspect-square w-32 h-32 bg-gradient-to-br from-slate-100 to-slate-200 rounded-xl flex items-center justify-center overflow-hidden">
                                        <Image src={image} alt={`Gallery icon ${index + 1}`} layout="fill"
                                               className="object-cover"/>
                                    </div>
                                    <div
                                        className="absolute inset-0 bg-gradient-to-br from-blue-600/0 to-purple-600/0 group-hover:from-blue-600/10 group-hover:to-purple-600/10 rounded-2xl transition-all duration-300"></div>
                                </div>
                            ))}
                        </div>
                        <div className="flex w-max animate-scroll-left-to-right-slow">
                            {[...galleryImagesRow3, ...galleryImagesRow3].map((image, index) => (
                                <div key={index}
                                     className="group relative bg-white/80 backdrop-blur-md rounded-2xl p-6 shadow-lg border border-purple-200/30 hover:shadow-xl transition-all duration-300 hover:scale-[1.05] cursor-pointer mx-3">
                                    <div
                                        className="relative aspect-square w-32 h-32 bg-gradient-to-br from-slate-100 to-slate-200 rounded-xl flex items-center justify-center overflow-hidden">
                                        <Image src={image} alt={`Gallery icon ${index + 1}`} layout="fill"
                                               className="object-cover"/>
                                    </div>
                                    <div
                                        className="absolute inset-0 bg-gradient-to-br from-blue-600/0 to-purple-600/0 group-hover:from-blue-600/10 group-hover:to-purple-600/10 rounded-2xl transition-all duration-300"></div>
                                </div>
                            ))}
                        </div>
                    </div>
                </div>
            </section>

            {/* Illustrations Gallery Section */}
            <section className="px-6 py-16 bg-gradient-to-br from-pink-50/50 to-rose-50/50">
                <div className="max-w-7xl mx-auto">
                    <div className="text-center mb-12">
                        <h2 className="text-4xl font-bold text-slate-900 mb-4">
                            Generated Illustrations Gallery
                        </h2>
                    </div>

                    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-8">
                        {illustrationImages.map((image, index) => (
                            <div key={index}
                                 onClick={() => openImageModal(image)}
                                 className="group relative bg-white/80 backdrop-blur-md rounded-2xl p-6 shadow-lg border border-pink-200/30 hover:shadow-xl transition-all duration-300 hover:scale-[1.05] cursor-pointer">
                                <div
                                    className="relative aspect-[4/3] w-full bg-gradient-to-br from-slate-100 to-slate-200 rounded-xl flex items-center justify-center overflow-hidden">
                                    <Image src={image} alt={`Illustration ${index + 1}`} layout="fill"
                                           className="object-cover"/>
                                </div>
                                <div
                                    className="absolute inset-0 bg-gradient-to-br from-rose-500/0 to-purple-500/0 group-hover:from-rose-500/10 group-hover:to-purple-500/10 rounded-2xl transition-all duration-300"></div>
                            </div>
                        ))}
                    </div>
                </div>
            </section>

            {/* UI Mockups Gallery Section */}
            <section className="px-6 py-16 bg-gradient-to-br from-teal-50/50 to-blue-50/50">
                <div className="max-w-7xl mx-auto">
                    <div className="text-center mb-12">
                        <h2 className="text-4xl font-bold text-slate-900 mb-4">
                            Generated UI Mockups Gallery
                        </h2>
                    </div>

                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-8">
                        {mockupImages.map((image, index) => (
                            <div key={index}
                                 onClick={() => openImageModal(image)}
                                 className="group relative bg-white/80 backdrop-blur-md rounded-2xl p-6 shadow-lg border border-teal-200/30 hover:shadow-xl transition-all duration-300 hover:scale-[1.05] cursor-pointer">
                                <div
                                    className="relative aspect-[16/9] w-full bg-gradient-to-br from-slate-100 to-slate-200 rounded-xl flex items-center justify-center overflow-hidden">
                                    <Image src={image} alt={`UI Mockup ${index + 1}`} layout="fill"
                                           className="object-cover"/>
                                </div>
                                <div
                                    className="absolute inset-0 bg-gradient-to-br from-teal-500/0 to-blue-500/0 group-hover:from-teal-500/10 group-hover:to-blue-500/10 rounded-2xl transition-all duration-300"></div>
                            </div>
                        ))}
                    </div>
                </div>
            </section>

            {/* CTA Section */}
            <section className="px-6 py-16">
                <div className="max-w-4xl mx-auto text-center">
                    <div
                        className="bg-gradient-to-br from-blue-600 to-purple-600 rounded-3xl p-12 text-white relative overflow-hidden">
                        {/* Background decorations */}
                        <div className="absolute top-0 left-0 w-32 h-32 bg-white/10 rounded-full blur-3xl"></div>
                        <div
                            className="absolute bottom-0 right-0 w-40 h-40 bg-purple-300/20 rounded-full blur-3xl"></div>

                        <div className="relative z-10">
                            <h2 className="text-4xl font-bold mb-6">
                                Ready to Create Amazing Icons?
                            </h2>
                            <p className="text-xl mb-8 text-blue-100">
                                Join designers and developers who trust our AI-powered icon generator
                            </p>
                            <div className="flex flex-col sm:flex-row gap-4 justify-center">
                                <button
                                    onClick={handleStartCreating}
                                    className="bg-white text-blue-600 hover:bg-blue-50 font-semibold py-4 px-8 rounded-xl shadow-lg hover:shadow-xl transform hover:scale-[1.02] transition-all duration-200"
                                >
                                    Start Generating Icons
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            </section>

            {/* Login Modal - Using shared component */}
            <LoginModal
                isOpen={isLoginModalOpen}
                isVisible={isModalVisible}
                onClose={closeLoginModal}
                onSuccess={handleLoginSuccess}
            />

            {selectedImage && (
                <div
                    className="fixed inset-0 bg-black/80 backdrop-blur-sm flex items-center justify-center z-50"
                    onClick={closeImageModal}
                >
                    <div className="relative max-w-4xl max-h-[90vh] p-4" onClick={(e) => e.stopPropagation()}>
                        <Image
                            src={selectedImage}
                            alt="Full-size image"
                            width={1920}
                            height={1080}
                            className="object-contain w-full h-full rounded-lg"
                        />
                        <button
                            onClick={closeImageModal}
                            className="absolute top-[-1rem] right-[-1rem] text-white bg-black/50 rounded-full p-2"
                        >
                            <svg xmlns="http://www.w3.org/2000/svg" className="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                            </svg>
                        </button>
                    </div>
                </div>
            )}

            <Footer/>
        </div>
    );
}
