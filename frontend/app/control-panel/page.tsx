"use client";

import { useState, useEffect } from "react";
import { useRouter } from "next/navigation";
import Navigation from "../../components/Navigation";
import { useAuth } from "../../context/AuthContext";
import { X, ChevronUp, ChevronDown, ChevronsUpDown } from "lucide-react";

interface UserAdminData {
  id: number;
  email: string;
  lastLogin: string | null;
  coins: number;
  trialCoins: number;
  generatedIconsCount: number;
  generatedIllustrationsCount: number;
  registeredAt: string;
  authProvider: string;
  isActive: boolean;
}

interface PagedResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
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

interface UserIllustration {
  imageUrl: string;
  description: string;
  serviceSource: string;
  requestId: string;
}

export default function ControlPanelPage() {
  const router = useRouter();
  const { authState } = useAuth();
  const [users, setUsers] = useState<UserAdminData[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedUser, setSelectedUser] = useState<UserAdminData | null>(null);
  const [userIcons, setUserIcons] = useState<UserIcon[]>([]);
  const [userIllustrations, setUserIllustrations] = useState<UserIllustration[]>(
    []
  );
  const [loadingIcons, setLoadingIcons] = useState(false);
  const [loadingIllustrations, setLoadingIllustrations] = useState(false);
  const [showIconsModal, setShowIconsModal] = useState(false);
  const [showIllustrationsModal, setShowIllustrationsModal] = useState(false);
  const [showSetCoinsModal, setShowSetCoinsModal] = useState(false);
  const [userForCoins, setUserForCoins] = useState<UserAdminData | null>(null);
  const [coins, setCoins] = useState(0);
  const [trialCoins, setTrialCoins] = useState(0);
  
  // Sorting state
  const [sortColumn, setSortColumn] = useState<keyof UserAdminData | null>(null);
  const [sortDirection, setSortDirection] = useState<"asc" | "desc">("asc");
  
  // Pagination state (server-side)
  const [currentPage, setCurrentPage] = useState(0); // Backend uses 0-based indexing
  const [itemsPerPage, setItemsPerPage] = useState(10);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalIcons, setTotalIcons] = useState(0);
  const [totalIllustrations, setTotalIllustrations] = useState(0);

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
  }, [authState, router, currentPage, itemsPerPage, sortColumn, sortDirection]);

  const fetchUsers = async () => {
    try {
      // Map frontend column names to backend field names
      const sortFieldMap: Record<string, string> = {
        email: "email",
        lastLogin: "lastLogin",
        coins: "coins",
        trialCoins: "trialCoins",
        generatedIconsCount: "id", // We'll sort by id as proxy since counts are calculated
        generatedIllustrationsCount: "id",
        registeredAt: "registeredAt",
        authProvider: "authProvider",
        isActive: "isActive",
      };

      const sortField = sortColumn ? sortFieldMap[sortColumn] || "id" : "id";
      const params = new URLSearchParams({
        page: currentPage.toString(),
        size: itemsPerPage.toString(),
        sortBy: sortField,
        direction: sortDirection,
      });

      const response = await fetch(`/api/admin/users?${params}`, {
        credentials: "include",
      });

      if (response.status === 403) {
        router.push("/dashboard");
        return;
      }

      if (!response.ok) {
        throw new Error("Failed to fetch users");
      }

      const data: PagedResponse<UserAdminData> = await response.json();
      setUsers(data.content);
      setTotalElements(data.totalElements);
      setTotalPages(data.totalPages);
      
      // Calculate total icons and illustrations
      const iconsSum = data.content.reduce((sum, user) => sum + user.generatedIconsCount, 0);
      const illustrationsSum = data.content.reduce((sum, user) => sum + user.generatedIllustrationsCount, 0);
      setTotalIcons(iconsSum);
      setTotalIllustrations(illustrationsSum);
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

  const fetchUserIllustrations = async (userId: number) => {
    setLoadingIllustrations(true);
    try {
      const response = await fetch(`/api/admin/users/${userId}/illustrations`, {
        credentials: "include",
      });

      if (!response.ok) {
        throw new Error("Failed to fetch user illustrations");
      }

      const data: UserIllustration[] = await response.json();
      setUserIllustrations(data);
    } catch (err: any) {
      console.error("Error fetching user illustrations:", err);
      setUserIllustrations([]);
    } finally {
      setLoadingIllustrations(false);
    }
  };

  const handleViewIcons = async (user: UserAdminData) => {
    setSelectedUser(user);
    setShowIconsModal(true);
    await fetchUserIcons(user.id);
  };

  const handleViewIllustrations = async (user: UserAdminData) => {
    setSelectedUser(user);
    setShowIllustrationsModal(true);
    await fetchUserIllustrations(user.id);
  };

  const closeIconsModal = () => {
    setShowIconsModal(false);
    setSelectedUser(null);
    setUserIcons([]);
  };

  const closeIllustrationsModal = () => {
    setShowIllustrationsModal(false);
    setSelectedUser(null);
    setUserIllustrations([]);
  };

  const handleOpenSetCoinsModal = (user: UserAdminData) => {
    setUserForCoins(user);
    setCoins(user.coins);
    setTrialCoins(user.trialCoins);
    setShowSetCoinsModal(true);
  };

  const handleCloseSetCoinsModal = () => {
    setShowSetCoinsModal(false);
    setUserForCoins(null);
    setCoins(0);
    setTrialCoins(0);
  };

  const handleSaveCoins = async () => {
    if (!userForCoins) return;

    try {
      const response = await fetch(`/api/admin/users/${userForCoins.id}/coins`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        credentials: "include",
        body: JSON.stringify({
          coins: Number(coins),
          trialCoins: Number(trialCoins),
        }),
      });

      if (!response.ok) {
        const errorData = await response
          .json()
          .catch(() => ({ message: "Failed to update coins" }));
        throw new Error(errorData.message || "Failed to update coins");
      }

      handleCloseSetCoinsModal();
      await fetchUsers(); // Refresh user list
    } catch (err: any) {
      console.error(err.message);
      alert(`Error: ${err.message}`);
    }
  };

  const formatDate = (dateString: string | null) => {
    if (!dateString) return "Never";
    const date = new Date(dateString);
    return date.toLocaleDateString() + " " + date.toLocaleTimeString();
  };

  // Sorting handler (server-side)
  const handleSort = (column: keyof UserAdminData) => {
    if (sortColumn === column) {
      // Toggle direction if same column
      setSortDirection(sortDirection === "asc" ? "desc" : "asc");
    } else {
      // New column, default to ascending
      setSortColumn(column);
      setSortDirection("asc");
    }
    // Reset to first page when sorting changes
    setCurrentPage(0);
  };

  // Pagination handlers (server-side, 0-based indexing)
  const goToPage = (page: number) => {
    setCurrentPage(Math.max(0, Math.min(page, totalPages - 1)));
  };

  // Render sort icon
  const renderSortIcon = (column: keyof UserAdminData) => {
    if (sortColumn !== column) {
      return <ChevronsUpDown className="w-4 h-4 ml-1 text-slate-400" />;
    }
    return sortDirection === "asc" ? (
      <ChevronUp className="w-4 h-4 ml-1 text-slate-700" />
    ) : (
      <ChevronDown className="w-4 h-4 ml-1 text-slate-700" />
    );
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
                  <th
                    className="px-6 py-3 text-left text-xs font-semibold text-slate-700 uppercase tracking-wider cursor-pointer hover:bg-purple-100 transition-colors"
                    onClick={() => handleSort("email")}
                  >
                    <div className="flex items-center">
                      Email
                      {renderSortIcon("email")}
                    </div>
                  </th>
                  <th
                    className="px-6 py-3 text-left text-xs font-semibold text-slate-700 uppercase tracking-wider cursor-pointer hover:bg-purple-100 transition-colors"
                    onClick={() => handleSort("lastLogin")}
                  >
                    <div className="flex items-center">
                      Last Login
                      {renderSortIcon("lastLogin")}
                    </div>
                  </th>
                  <th
                    className="px-6 py-3 text-left text-xs font-semibold text-slate-700 uppercase tracking-wider cursor-pointer hover:bg-purple-100 transition-colors"
                    onClick={() => handleSort("trialCoins")}
                  >
                    <div className="flex items-center">
                      Trial Coins
                      {renderSortIcon("trialCoins")}
                    </div>
                  </th>
                  <th
                    className="px-6 py-3 text-left text-xs font-semibold text-slate-700 uppercase tracking-wider cursor-pointer hover:bg-purple-100 transition-colors"
                    onClick={() => handleSort("coins")}
                  >
                    <div className="flex items-center">
                      Coins
                      {renderSortIcon("coins")}
                    </div>
                  </th>
                  <th
                    className="px-6 py-3 text-left text-xs font-semibold text-slate-700 uppercase tracking-wider cursor-pointer hover:bg-purple-100 transition-colors"
                    onClick={() => handleSort("generatedIconsCount")}
                  >
                    <div className="flex items-center">
                      Generated Icons
                      {renderSortIcon("generatedIconsCount")}
                    </div>
                  </th>
                  <th
                    className="px-6 py-3 text-left text-xs font-semibold text-slate-700 uppercase tracking-wider cursor-pointer hover:bg-purple-100 transition-colors"
                    onClick={() => handleSort("generatedIllustrationsCount")}
                  >
                    <div className="flex items-center">
                      Generated Illustrations
                      {renderSortIcon("generatedIllustrationsCount")}
                    </div>
                  </th>
                  <th
                    className="px-6 py-3 text-left text-xs font-semibold text-slate-700 uppercase tracking-wider cursor-pointer hover:bg-purple-100 transition-colors"
                    onClick={() => handleSort("registeredAt")}
                  >
                    <div className="flex items-center">
                      Registered
                      {renderSortIcon("registeredAt")}
                    </div>
                  </th>
                  <th
                    className="px-6 py-3 text-left text-xs font-semibold text-slate-700 uppercase tracking-wider cursor-pointer hover:bg-purple-100 transition-colors"
                    onClick={() => handleSort("authProvider")}
                  >
                    <div className="flex items-center">
                      Provider
                      {renderSortIcon("authProvider")}
                    </div>
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-semibold text-slate-700 uppercase tracking-wider">
                    Actions
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
                    <td className="px-6 py-4 whitespace-nowrap text-sm">
                      <button
                        onClick={() => handleViewIllustrations(user)}
                        className="inline-flex items-center px-3 py-1 rounded-md text-sm font-medium bg-gradient-to-r from-green-600 to-teal-600 text-white hover:from-green-700 hover:to-teal-700 transition-all duration-200"
                      >
                        View ({user.generatedIllustrationsCount})
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
                    <td className="px-6 py-4 whitespace-nowrap text-sm font-medium">
                      <button
                        onClick={() => handleOpenSetCoinsModal(user)}
                        className="text-indigo-600 hover:text-indigo-900"
                      >
                        Set Coins
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          
          {/* Pagination Controls */}
          <div className="px-6 py-4 bg-white border-t border-slate-200 flex items-center justify-between">
            <div className="flex items-center gap-4">
              <div className="text-sm text-slate-600">
                {totalElements > 0 ? (
                  <>Showing {currentPage * itemsPerPage + 1} to {Math.min((currentPage + 1) * itemsPerPage, totalElements)} of {totalElements} users</>
                ) : (
                  <>No users found</>
                )}
              </div>
              <div className="flex items-center gap-2">
                <label htmlFor="itemsPerPage" className="text-sm text-slate-600">
                  Per page:
                </label>
                <select
                  id="itemsPerPage"
                  value={itemsPerPage}
                  onChange={(e) => {
                    setItemsPerPage(Number(e.target.value));
                    setCurrentPage(0);
                  }}
                  className="px-2 py-1 border border-slate-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-purple-500"
                >
                  <option value={5}>5</option>
                  <option value={10}>10</option>
                  <option value={25}>25</option>
                  <option value={50}>50</option>
                  <option value={100}>100</option>
                </select>
              </div>
            </div>
            
            <div className="flex items-center gap-2">
              <button
                onClick={() => goToPage(0)}
                disabled={currentPage === 0}
                className="px-3 py-1 text-sm font-medium text-slate-700 bg-white border border-slate-300 rounded-md hover:bg-slate-50 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
              >
                First
              </button>
              <button
                onClick={() => goToPage(currentPage - 1)}
                disabled={currentPage === 0}
                className="px-3 py-1 text-sm font-medium text-slate-700 bg-white border border-slate-300 rounded-md hover:bg-slate-50 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
              >
                Previous
              </button>
              
              <div className="flex items-center gap-1">
                {Array.from({ length: Math.min(5, totalPages) }, (_, i) => {
                  let pageIndex;
                  if (totalPages <= 5) {
                    pageIndex = i;
                  } else if (currentPage <= 2) {
                    pageIndex = i;
                  } else if (currentPage >= totalPages - 3) {
                    pageIndex = totalPages - 5 + i;
                  } else {
                    pageIndex = currentPage - 2 + i;
                  }
                  
                  return (
                    <button
                      key={pageIndex}
                      onClick={() => goToPage(pageIndex)}
                      className={`px-3 py-1 text-sm font-medium rounded-md transition-colors ${
                        currentPage === pageIndex
                          ? "bg-purple-600 text-white"
                          : "text-slate-700 bg-white border border-slate-300 hover:bg-slate-50"
                      }`}
                    >
                      {pageIndex + 1}
                    </button>
                  );
                })}
              </div>
              
              <button
                onClick={() => goToPage(currentPage + 1)}
                disabled={currentPage === totalPages - 1}
                className="px-3 py-1 text-sm font-medium text-slate-700 bg-white border border-slate-300 rounded-md hover:bg-slate-50 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
              >
                Next
              </button>
              <button
                onClick={() => goToPage(totalPages - 1)}
                disabled={currentPage === totalPages - 1}
                className="px-3 py-1 text-sm font-medium text-slate-700 bg-white border border-slate-300 rounded-md hover:bg-slate-50 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
              >
                Last
              </button>
            </div>
          </div>
        </div>

        <div className="mt-6 text-sm text-slate-600">
          <p>Total Users: {totalElements}</p>
          <p>
            Total Icons Generated on this page:{" "}
            {totalIcons}
          </p>
          <p>
            Total Illustrations Generated on this page:{" "}
            {totalIllustrations}
          </p>
        </div>
      </div>

      {/* Set Coins Modal */}
      {showSetCoinsModal && userForCoins && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-lg max-w-md w-full">
            <div className="flex items-center justify-between p-6 border-b border-slate-200">
              <h2 className="text-2xl font-bold text-slate-800">
                Set Coins for {userForCoins.email}
              </h2>
              <button
                onClick={handleCloseSetCoinsModal}
                className="p-2 hover:bg-slate-100 rounded-lg transition-colors"
              >
                <X className="w-6 h-6 text-slate-600" />
              </button>
            </div>
            <div className="p-6">
              <div className="mb-4">
                <label
                  htmlFor="trialCoins"
                  className="block text-sm font-medium text-slate-700"
                >
                  Trial Coins
                </label>
                <input
                  type="number"
                  id="trialCoins"
                  value={trialCoins}
                  onChange={(e) => setTrialCoins(Number(e.target.value))}
                  className="mt-1 block w-full px-3 py-2 bg-white border border-slate-300 rounded-md shadow-sm focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm"
                />
              </div>
              <div className="mb-4">
                <label
                  htmlFor="coins"
                  className="block text-sm font-medium text-slate-700"
                >
                  Coins
                </label>
                <input
                  type="number"
                  id="coins"
                  value={coins}
                  onChange={(e) => setCoins(Number(e.target.value))}
                  className="mt-1 block w-full px-3 py-2 bg-white border border-slate-300 rounded-md shadow-sm focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm"
                />
              </div>
            </div>
            <div className="flex justify-end p-6 border-t border-slate-200">
              <button
                onClick={handleCloseSetCoinsModal}
                className="px-4 py-2 bg-slate-200 text-slate-700 rounded-lg hover:bg-slate-300 transition-colors mr-2"
              >
                Cancel
              </button>
              <button
                onClick={handleSaveCoins}
                className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
              >
                Save
              </button>
            </div>
          </div>
        </div>
      )}

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

      {/* Illustrations Modal */}
      {showIllustrationsModal && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-lg max-w-6xl w-full max-h-[90vh] overflow-hidden flex flex-col">
            <div className="flex items-center justify-between p-6 border-b border-slate-200">
              <div>
                <h2 className="text-2xl font-bold text-slate-800">
                  Illustrations for {selectedUser?.email}
                </h2>
                <p className="text-sm text-slate-600 mt-1">
                  Total: {userIllustrations.length} illustrations
                </p>
              </div>
              <button
                onClick={closeIllustrationsModal}
                className="p-2 hover:bg-slate-100 rounded-lg transition-colors"
              >
                <X className="w-6 h-6 text-slate-600" />
              </button>
            </div>

            <div className="flex-1 overflow-y-auto p-6">
              {loadingIllustrations ? (
                <div className="flex items-center justify-center py-12">
                  <p className="text-slate-600">Loading illustrations...</p>
                </div>
              ) : userIllustrations.length === 0 ? (
                <div className="flex items-center justify-center py-12">
                  <p className="text-slate-600">
                    No illustrations found for this user
                  </p>
                </div>
              ) : (
                <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-4">
                  {userIllustrations.map((illustration, index) => (
                    <div
                      key={index}
                      className="border rounded-lg p-2 bg-white shadow-sm hover:shadow-md transition-shadow"
                    >
                      <img
                        src={illustration.imageUrl}
                        alt={illustration.description || "Generated Illustration"}
                        className="w-full h-auto object-cover rounded-md"
                      />
                      <div className="mt-2 text-xs text-slate-600 truncate">
                        {illustration.description}
                      </div>
                      <div className="mt-1 text-xs text-slate-400 truncate">
                        {illustration.serviceSource}
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>

            <div className="flex justify-end p-6 border-t border-slate-200">
              <button
                onClick={closeIllustrationsModal}
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
