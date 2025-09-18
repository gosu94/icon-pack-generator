'use client';

import React from 'react';
import Link from 'next/link';
import Image from 'next/image';

const Footer: React.FC = () => {
  return (
    <footer className="bg-slate-900 text-white">
      <div className="max-w-7xl mx-auto px-6 py-12">
        <div className="grid grid-cols-1 md:grid-cols-4 gap-8">
          {/* Brand Section */}
          <div className="col-span-1 md:col-span-2">
            <div className="flex items-center space-x-3 mb-4">
              <Image
                src="/images/logo small.webp"
                alt="Icon Pack Generator"
                width={32}
                height={32}
              />
              <span className="text-xl font-semibold text-white">
                Icon Pack Generator
              </span>
            </div>
            <p className="text-slate-300 mb-6 max-w-md">
              Create stunning icon packs with AI-powered technology. Generate professional-quality icons in minutes, not hours.
            </p>
            <div className="flex items-center space-x-2 text-sm text-slate-400">
              <span>© 2025 Icon Pack Generator. All rights reserved.</span>
            </div>
          </div>

          {/* Product Links */}
          <div>
            <h3 className="text-lg font-semibold mb-4">Product</h3>
            <ul className="space-y-3 text-slate-300">
              <li>
                <Link href="/dashboard" className="hover:text-white transition-colors">
                  Icon Generator
                </Link>
              </li>
              <li>
                <Link href="/store" className="hover:text-white transition-colors">
                  Store
                </Link>
              </li>
            </ul>
          </div>

          {/* Support & Legal */}
          <div>
            <h3 className="text-lg font-semibold mb-4">Support & Legal</h3>
            <ul className="space-y-3 text-slate-300">
              <li>
                <a href="mailto:support@iconpackgen.com" className="hover:text-white transition-colors">
                  Contact Support
                </a>
              </li>
              <li>
                <Link href="/privacy" className="hover:text-white transition-colors">
                  Privacy Policy
                </Link>
              </li>
              <li>
                <Link href="/terms" className="hover:text-white transition-colors">
                  Terms & Conditions
                </Link>
              </li>
            </ul>
          </div>
        </div>

        {/* Bottom Section */}
        <div className="border-t border-slate-700 mt-12 pt-8 flex flex-col md:flex-row justify-between items-center">

          <div className="flex items-center space-x-6 text-sm text-slate-400">
            <Link href="/privacy" className="hover:text-white transition-colors">
              Privacy
            </Link>
            <Link href="/terms" className="hover:text-white transition-colors">
              Terms
            </Link>
            <a href="mailto:support@iconpackgen.com" className="hover:text-white transition-colors">
              Support
            </a>
          </div>
        </div>
      </div>
    </footer>
  );
};

export default Footer;

