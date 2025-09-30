"use client";

import { useState, useEffect } from "react";
import { useRouter } from "next/navigation";
import Navigation from "../../components/Navigation";
import { useAuth } from "../../context/AuthContext";
import { X } from "lucide-react";

interface UserAdminData {
  id: number;
  email: string;
  lastLogin: string | null;
  coins: number;
  trialCoins: number;
  generatedIconsCount: number;
  registeredAt: string;
  authProvider: string;
  isActive: boolean;
}

interface UserIcon {
  id: number;
  imageUrl: string;
  description: string;
  serviceSource: string;
  requestId: string;
  iconType: string;
  theme: string;
}

export default function ControlPanelPage() {
  const router = useRouter();
  const { authState } = useAuth();
  const [users, setUsers] = useState<UserAdminData[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedUser, setSelectedUser] = useState<UserAdminData | null>(null);
  const [userIcons, setUserIcons] = useState<UserIcon[]>([]);
  const [loadingIcons, setLoadingIcons] = useState(false);
  const [showIconsModal, setShowIconsModal] = useState(false);

  useEffect(() => {
    // Check if user is admin
    if (authState.authenticated === false) {
      router.push("/login");
      return;
    }

    if (authState.authenticated && authState.user && !authState.user.isAdmin) {
      // Non-admin user trying to access admin panel
      router.push("/dashboard");
      return;
    }

    if (authState.authenticated && authState.user?.isAdmin) {
      fetchUsers();
    }
  }, [authState, router]);

  const fetchUsers = async () => {
    try {
      const response = await fetch("/api/admin/users", {
        credentials: "include",
      });

      if (response.status === 403) {
        router.push("/dashboard");
        return;
      }

      if (!response.ok) {
        throw new Error("Failed to fetch users");
      }

      const data: UserAdminData[] = await response.json();
      setUsers(data);
    } catch (err: any) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  const fetchUserIcons = async (userId: number) => {
    setLoadingIcons(true);
    try {
      const response = await fetch(`/api/admin/users/${userId}/icons`, {
        credentials: "include",
      });

      if (!response.ok) {
        throw new Error("Failed to fetch user icons");
      }

      const data: UserIcon[] = await response.json();
      setUserIcons(data);
    } catch (err: any) {
      console.error("Error fetching user icons:", err);
      setUserIcons([]);
    } finally {
      setLoadingIcons(false);
    }
  };

  const handleViewIcons = async (user: UserAdminData) => {
    setSelectedUser(user);
    setShowIconsModal(true);
    await fetchUserIcons(user.id);
  };

  const closeIconsModal = () => {
    setShowIconsModal(false);
    setSelectedUser(null);
    setUserIcons([]);
  };

  const formatDate = (dateString: string | null) => {
    if (!dateString) return "Never";
    const date = new Date(dateString);
    return date.toLocaleDateString() + " " + date.toLocaleTimeString();
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-gradient-to-br from-slate-50 via-white to-blue-50/30">
        <Navigation useLoginPage={true} />
        <div className="container mx-auto px-4 py-8">
          <p>Loading...</p>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="min-h-screen bg-gradient-to-br from-slate-50 via-white to-blue-50/30">
        <Navigation useLoginPage={true} />
        <div className="container mx-auto px-4 py-8">
          <p className="text-red-500">{error}</p>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-50 via-white to-blue-50/30">
      <Navigation useLoginPage={true} />
      <div className="container mx-auto px-4 py-8">
        <div className="mb-8">
          <h1 className="text-3xl font-bold text-slate-800 mb-2">
            Admin Control Panel
          </h1>
          <p className="text-slate-600">
            Manage users and view system statistics
          </p>
        </div>

        <div className="bg-white/50 rounded-lg border border-slate-200/80 shadow-lg shadow-slate-200/50 overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead className="bg-purple-50 border-b border-slate-200">
                <tr>
                  <th className="px-6 py-3 text-left text-xs font-semibold text-slate-700 uppercase tracking-wider">
                    Email
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-semibold text-slate-700 uppercase tracking-wider">
                    Last Login
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-semibold text-slate-700 uppercase tracking-wider">
                    Trial Coins
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-semibold text-slate-700 uppercase tracking-wider">
                    Coins
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-semibold text-slate-700 uppercase tracking-wider">
                    Generated Icons
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-semibold text-slate-700 uppercase tracking-wider">
                    Registered
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-semibold text-slate-700 uppercase tracking-wider">
                    Provider
                  </th>
                </tr>
              </thead>
              <tbody className="bg-white divide-y divide-slate-100">
                {users.map((user) => (
                  <tr
                    key={user.id}
                    className="hover:bg-slate-50 transition-colors"
                  >
                    <td className="px-6 py-4 whitespace-nowrap text-sm text-slate-900">
                      {user.email}
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-sm text-slate-600">
                      {formatDate(user.lastLogin)}
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-sm text-slate-900">
                      <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-green-100 text-green-800">
                        {user.trialCoins}
                      </span>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-sm text-slate-900">
                      <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-yellow-100 text-yellow-800">
                        {user.coins}
                      </span>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-sm">
                      <button
                        onClick={() => handleViewIcons(user)}
                        className="inline-flex items-center px-3 py-1 rounded-md text-sm font-medium bg-gradient-to-r from-blue-600 to-purple-600 text-white hover:from-blue-700 hover:to-purple-700 transition-all duration-200"
                      >
                        View ({user.generatedIconsCount})
                      </button>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-sm text-slate-600">
                      {formatDate(user.registeredAt)}
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-sm text-slate-600">
                      <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-blue-100 text-blue-800">
                        {user.authProvider}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>

        <div className="mt-6 text-sm text-slate-600">
          <p>Total Users: {users.length}</p>
          <p>
            Total Icons Generated:{" "}
            {users.reduce((sum, user) => sum + user.generatedIconsCount, 0)}
          </p>
        </div>
      </div>

      {/* Icons Modal */}
      {showIconsModal && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-lg max-w-6xl w-full max-h-[90vh] overflow-hidden flex flex-col">
            <div className="flex items-center justify-between p-6 border-b border-slate-200">
              <div>
                <h2 className="text-2xl font-bold text-slate-800">
                  Icons for {selectedUser?.email}
                </h2>
                <p className="text-sm text-slate-600 mt-1">
                  Total: {userIcons.length} icons
                </p>
              </div>
              <button
                onClick={closeIconsModal}
                className="p-2 hover:bg-slate-100 rounded-lg transition-colors"
              >
                <X className="w-6 h-6 text-slate-600" />
              </button>
            </div>

            <div className="flex-1 overflow-y-auto p-6">
              {loadingIcons ? (
                <div className="flex items-center justify-center py-12">
                  <p className="text-slate-600">Loading icons...</p>
                </div>
              ) : userIcons.length === 0 ? (
                <div className="flex items-center justify-center py-12">
                  <p className="text-slate-600">No icons found for this user</p>
                </div>
              ) : (
                <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 gap-4">
                  {userIcons.map((icon, index) => (
                    <div
                      key={index}
                      className="border rounded-lg p-2 bg-white shadow-sm hover:shadow-md transition-shadow"
                    >
                      <img
                        src={icon.imageUrl}
                        alt={icon.description || "Generated Icon"}
                        className="w-full h-auto object-cover rounded-md"
                      />
                      <div className="mt-2 text-xs text-slate-600 truncate">
                        {icon.theme || icon.description}
                      </div>
                      <div className="mt-1 text-xs text-slate-400 truncate">
                        {icon.serviceSource}
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>

            <div className="flex justify-end p-6 border-t border-slate-200">
              <button
                onClick={closeIconsModal}
                className="px-4 py-2 bg-slate-200 text-slate-700 rounded-lg hover:bg-slate-300 transition-colors"
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
