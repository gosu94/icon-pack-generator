'use client';

import React, { useState, useEffect, useRef } from "react";
import Link from "next/link";
import Image from "next/image";
import {
    Image as ImageIcon,
    LogOut,
    MessageSquare,
    Paintbrush,
    Settings,
    Store,
    User,
    Sparkles,
    Menu,
} from "lucide-react";
import { useAuth } from "../context/AuthContext";
import LoginModal from "./LoginModal";

interface NavigationProps {
  useLoginPage?: boolean; // If true, redirect to /login instead of showing modal
}

const Navigation: React.FC<NavigationProps> = ({ useLoginPage = false }) => {
  const { authState, coinsLoading, handleLogout } = useAuth();
  const [isLoginModalOpen, setIsLoginModalOpen] = useState(false);
  const [isModalVisible, setIsModalVisible] = useState(false);
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);
  const mobileMenuRef = useRef<HTMLDivElement>(null);

  const openLoginModal = () => {
    if (useLoginPage) {
      // Redirect to login page with current path as redirect parameter
      const currentPath = window.location.pathname;
      const loginUrl = currentPath === '/' ? '/login' : `/login?redirect=${encodeURIComponent(currentPath)}`;
      window.location.href = loginUrl;
    } else {
      setIsLoginModalOpen(true);
      setTimeout(() => setIsModalVisible(true), 10);
    }
  };

  const closeLoginModal = () => {
    setIsModalVisible(false);
    setTimeout(() => {
      setIsLoginModalOpen(false);
    }, 300);
  };


  const toggleMobileMenu = () => {
    setIsMobileMenuOpen(!isMobileMenuOpen);
  };

  const displayCoins =
    authState.authenticated && authState.user ? authState.user.coins : 0;
  const displayTrialCoins = 
    authState.authenticated && authState.user ? authState.user.trialCoins : 0;

  // Handle clicking outside mobile menu to close it
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (mobileMenuRef.current && !mobileMenuRef.current.contains(event.target as Node)) {
        setIsMobileMenuOpen(false);
      }
    };

    const handleResize = () => {
      setIsMobileMenuOpen(false);
    };

    if (isMobileMenuOpen) {
      document.addEventListener('mousedown', handleClickOutside);
      window.addEventListener('resize', handleResize);
    }

    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
      window.removeEventListener('resize', handleResize);
    };
  }, [isMobileMenuOpen]);

  return (
    <nav className="border-b border-gray-200 px-6 py-4">
      <div className="flex items-center justify-between">
        <div className="flex items-center space-x-6">
          <div className="flex items-center space-x-3">
            <Image
              src="/images/logo small.webp"
              alt="Icon Pack Generator"
              width={32}
              height={32}
            />
            <Link href="/">
              <span className="text-xl font-medium text-black cursor-pointer">
                Icon Pack Generator
                <span className="inline-flex items-center ml-2">
                  <span className="font-bold bg-clip-text text-transparent bg-gradient-to-b from-purple-400 to-purple-700">
                    AI
                  </span>
                  <Sparkles className="w-3 h-3 text-purple-400 ml-1" />
                </span>
              </span>
            </Link>
          </div>
        </div>

        <div className="flex items-center space-x-4">
          {authState.authenticated ? (
            <>
              {/* Coin Balance Display - always visible for authenticated users */}
              <div className="flex items-center space-x-1">
                {/* Trial Coins - shown only if user has trial coins */}
                {displayTrialCoins > 0 && (
                  <div className="flex items-center space-x-2 rounded-lg px-3 py-2">
                    <div className="w-5 h-5 bg-green-500 rounded-full flex items-center justify-center">
                      <span className="text-xs font-bold text-white">T</span>
                    </div>
                    <span className="text-sm font-semibold text-green-800">
                      {coinsLoading ? "..." : displayTrialCoins}
                    </span>
                  </div>
                )}
                
                {/* Regular Coins */}
                <div className="flex items-center space-x-2 rounded-lg px-3 py-2">
                  <Image
                    src="/images/coin.webp"
                    alt="Coins"
                    width={20}
                    height={20}
                  />
                  <span className="text-sm font-semibold text-yellow-800">
                    {coinsLoading ? "..." : displayCoins}
                  </span>
                </div>
              </div>

              {/* Desktop Navigation - hidden on mobile */}
              <div className="hidden md:flex items-center space-x-4">
                <Link href="/dashboard" className="p-2 hover:bg-gray-100 rounded-lg">
                  <Sparkles className="w-5 h-5 text-gray-700" />
                </Link>
                <Link
                  href="/gallery"
                  className="p-2 hover:bg-gray-100 rounded-lg"
                >
                  <ImageIcon className="w-5 h-5 text-gray-700" />
                </Link>
                <Link href="/store" className="p-2 hover:bg-gray-100 rounded-lg">
                  <Store className="w-5 h-5 text-gray-700" />
                </Link>
                <Link href="/feedback" className="p-2 hover:bg-gray-100 rounded-lg">
                  <MessageSquare className="w-5 h-5 text-gray-700" />
                </Link>
                <Link href="/settings" className="p-2 hover:bg-gray-100 rounded-lg">
                  <Settings className="w-5 h-5 text-gray-700" />
                </Link>
                <button
                  onClick={handleLogout}
                  className="p-2 hover:bg-gray-100 rounded-lg"
                  title="Logout"
                >
                  <LogOut className="w-5 h-5 text-gray-700" />
                </button>
              </div>

              {/* Mobile Menu Button - visible only on mobile */}
              <div className="md:hidden relative" ref={mobileMenuRef}>
                <button
                  onClick={toggleMobileMenu}
                  className="p-2 hover:bg-gray-100 rounded-lg"
                  aria-label="Toggle mobile menu"
                >
                  <Menu className="w-5 h-5 text-gray-700" />
                </button>

                {/* Mobile Dropdown Menu */}
                {isMobileMenuOpen && (
                  <div className="absolute right-0 top-12 w-48 bg-white border border-gray-200 rounded-lg shadow-lg z-50">
                    <div className="py-2">
                      <Link 
                        href="/dashboard" 
                        className="flex items-center space-x-3 px-4 py-3 hover:bg-gray-100"
                        onClick={() => setIsMobileMenuOpen(false)}
                      >
                        <Sparkles className="w-4 h-4 text-gray-700" />
                        <span className="text-sm font-medium text-gray-700">Dashboard</span>
                      </Link>
                      <Link 
                        href="/gallery" 
                        className="flex items-center space-x-3 px-4 py-3 hover:bg-gray-100"
                        onClick={() => setIsMobileMenuOpen(false)}
                      >
                        <ImageIcon className="w-4 h-4 text-gray-700" />
                        <span className="text-sm font-medium text-gray-700">Gallery</span>
                      </Link>
                      <Link
                        href="/store" 
                        className="flex items-center space-x-3 px-4 py-3 hover:bg-gray-100"
                        onClick={() => setIsMobileMenuOpen(false)}
                      >
                        <Store className="w-4 h-4 text-gray-700" />
                        <span className="text-sm font-medium text-gray-700">Store</span>
                      </Link>
                      <Link 
                        href="/feedback" 
                        className="flex items-center space-x-3 px-4 py-3 hover:bg-gray-100"
                        onClick={() => setIsMobileMenuOpen(false)}
                      >
                        <MessageSquare className="w-4 h-4 text-gray-700" />
                        <span className="text-sm font-medium text-gray-700">Feedback</span>
                      </Link>
                      <Link 
                        href="/settings"
                        className="flex items-center space-x-3 px-4 py-3 hover:bg-gray-100"
                        onClick={() => setIsMobileMenuOpen(false)}
                      >
                        <Settings className="w-4 h-4 text-gray-700" />
                        <span className="text-sm font-medium text-gray-700">Settings</span>
                      </Link>
                      <div className="border-t border-gray-100 my-2"></div>
                      <button
                        onClick={() => {
                          handleLogout();
                          setIsMobileMenuOpen(false);
                        }}
                        className="flex items-center space-x-3 px-4 py-3 hover:bg-gray-100 w-full text-left"
                      >
                        <LogOut className="w-4 h-4 text-red-600" />
                        <span className="text-sm font-medium text-red-600">Logout</span>
                      </button>
                    </div>
                  </div>
                )}
              </div>
            </>
          ) : (
            <>
              {/* Sign In Button for unauthenticated users */}
              <button
                onClick={openLoginModal}
                className="flex items-center space-x-2 px-4 py-2 rounded-lg text-white font-semibold bg-gradient-to-r from-blue-600 to-purple-600 hover:from-blue-700 hover:to-purple-700 transform hover:scale-[1.02] shadow-lg hover:shadow-xl transition-all duration-200"
              >
                <User className="w-4 h-4" />
                <span className="text-sm font-medium">Sign In</span>
              </button>
            </>
          )}
        </div>
      </div>

      {/* Login Modal - only show if not using login page */}
      {!useLoginPage && (
        <LoginModal 
          isOpen={isLoginModalOpen}
          isVisible={isModalVisible}
          onClose={closeLoginModal}
        />
      )}
    </nav>
  );
};

export default Navigation;