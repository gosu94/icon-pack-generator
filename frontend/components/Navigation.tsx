import React from "react";
import Link from "next/link";
import {
  Home,
  Image as ImageIcon,
  LogOut,
  Menu,
  MessageSquare,
  Paintbrush,
  Settings,
} from "lucide-react";

const Navigation = () => {
  return (
    <nav className="border-b border-gray-200 px-6 py-4">
      <div className="flex items-center justify-between">
        <div className="flex items-center space-x-6">
          <div className="flex items-center space-x-3">
            <div className="w-8 h-8 bg-black rounded-lg flex items-center justify-center">
              <Menu className="w-5 h-5 text-white" />
            </div>
            <Link href="/">
              <span className="text-xl font-medium text-black cursor-pointer">
                Icon Pack Generator
              </span>
            </Link>
          </div>
        </div>

        <div className="flex items-center space-x-4">
          <Link href="/" className="p-2 hover:bg-gray-100 rounded-lg">
            <Menu className="w-5 h-5 text-gray-700" />
          </Link>
          <Link href="/gallery" className="p-2 hover:bg-gray-100 rounded-lg">
            <ImageIcon className="w-5 h-5 text-gray-700" />
          </Link>
          <Link
            href="/background-remover"
            className="p-2 hover:bg-gray-100 rounded-lg"
          >
            <Paintbrush className="w-5 h-5 text-gray-700" />
          </Link>
          <button className="p-2 hover:bg-gray-100 rounded-lg">
            <Home className="w-5 h-5 text-gray-700" />
          </button>
          <button className="p-2 hover:bg-gray-100 rounded-lg">
            <MessageSquare className="w-5 h-5 text-gray-700" />
          </button>
          <button className="p-2 hover:bg-gray-100 rounded-lg">
            <Settings className="w-5 h-5 text-gray-700" />
          </button>
          <button className="p-2 hover:bg-gray-100 rounded-lg">
            <LogOut className="w-5 h-5 text-gray-700" />
          </button>
        </div>
      </div>
    </nav>
  );
};

export default Navigation;
