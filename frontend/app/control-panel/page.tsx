"use client";

import { useState, useEffect } from "react";
import { useRouter } from "next/navigation";
import Navigation from "../../components/Navigation";
import { useAuth } from "../../context/AuthContext";
import { X, ChevronUp, ChevronDown, ChevronsUpDown } from "lucide-react";
import {
  UserAdminData,
  PagedResponse,
  UserIcon,
  UserIllustration,
  UserMockup,
} from "./types";
import UsersTab from "./components/UsersTab";
import EmailTab from "./components/EmailTab";

const DEFAULT_EMAIL_BODY = `<!DOCTYPE html>
<html lang="en">
  <head>
    <meta charset="UTF-8" />
    <title>IconPackGen AI Update</title>
    <style>
      body {
        margin: 0;
        padding: 0;
        background: #f8fafc;
        font-family: 'Inter', system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
        color: #0f172a;
      }
      .email-wrapper {
        width: 100%;
        padding: 32px 16px;
        background: linear-gradient(135deg, #eef2ff, #f5f3ff);
      }
      .email-card {
        max-width: 640px;
        margin: 0 auto;
        background: #ffffff;
        border-radius: 24px;
        box-shadow: 0 30px 60px rgba(79, 70, 229, 0.12);
        overflow: hidden;
        border: 1px solid rgba(79, 70, 229, 0.08);
      }
      .email-header {
        display: flex;
        align-items: center;
        gap: 16px;
        padding: 32px;
        background: linear-gradient(135deg, rgba(99, 102, 241, 0.12), rgba(124, 58, 237, 0.08));
      }
      .email-header h1 {
        margin: 0;
        font-size: 26px;
        font-weight: 700;
        color: #0f172a;
      }
      .email-header .brand-accent {
        background: linear-gradient(135deg, #6366f1 0%, #a855f7 100%);
        -webkit-background-clip: text;
        color: transparent;
        display: inline-flex;
        align-items: center;
        gap: 4px;
      }
      .email-header .brand-accent span {
        font-size: 18px;
      }
      .email-content {
        padding: 32px;
      }
      .email-content h2 {
        font-size: 22px;
        margin: 0 0 16px;
        color: #312e81;
      }
      .email-content p {
        margin: 0 0 16px;
        line-height: 1.6;
        color: #334155;
        font-size: 16px;
      }
      .feature-list {
        padding: 0;
        margin: 24px 0 32px;
        list-style: none;
      }
      .feature-list li {
        margin-bottom: 16px;
        padding-left: 32px;
        position: relative;
        font-size: 15px;
        color: #1e293b;
      }
      .feature-list li::before {
        content: '✨';
        position: absolute;
        left: 0;
        top: 0;
      }
      .cta-button {
        display: inline-block;
        padding: 14px 32px;
        background: linear-gradient(135deg, #6366f1 0%, #a855f7 100%);
        color: #ffffff !important;
        border-radius: 9999px;
        text-decoration: none;
        font-weight: 600;
        box-shadow: 0 20px 35px rgba(99, 102, 241, 0.35);
      }
      .email-footer {
        padding: 24px 32px 32px;
        background: #f8fafc;
        border-top: 1px solid rgba(99, 102, 241, 0.08);
        font-size: 13px;
        color: #64748b;
        line-height: 1.6;
      }
      @media (max-width: 600px) {
        .email-header,
        .email-content,
        .email-footer {
          padding: 24px;
        }
        .email-header h1 {
          font-size: 22px;
        }
      }
    </style>
  </head>
  <body>
    <div class="email-wrapper">
      <div class="email-card">
        <div class="email-header">
          <img
            src="https://iconpackgen.com/images/logo%20small.webp"
            alt="IconPackGen AI"
            width="48"
            height="48"
            style="border-radius: 12px"
          />
          <h1>
            IconPackGen
            <span class="brand-accent">AI <span>✨</span></span>
          </h1>
        </div>
        <div class="email-content">
          <h2>Hey there,</h2>
          <p>
            We have something exciting to share with you from IconPackGen AI. Here’s a quick
            overview of the latest updates, improvements, and insider tips to help you create your
            next standout project.
          </p>
          <ul class="feature-list">
            <li>Add your feature highlight or announcement here.</li>
            <li>Share an upcoming launch, workshop, or promotion.</li>
            <li>Include a helpful tip, resource, or community spotlight.</li>
          </ul>
          <a
            href="https://iconpackgen.com/dashboard"
            class="cta-button"
            target="_blank"
            rel="noopener"
          >
            Jump back into IconPackGen
          </a>
        </div>
        <div class="email-footer">
          <p>
            IconPackGen AI &bull; Crafted with creativity for designers and teams around the globe.
          </p>
          <p style="margin-top: 12px">
            You’re receiving this email because you’re part of the IconPackGen community. If you want to
            unsubscribe - just let me know.
          </p>
        </div>
      </div>
    </div>
  </body>
</html>`;

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
  const [userMockups, setUserMockups] = useState<UserMockup[]>([]);
  const [loadingIcons, setLoadingIcons] = useState(false);
  const [loadingIllustrations, setLoadingIllustrations] = useState(false);
  const [loadingMockups, setLoadingMockups] = useState(false);
  const [showIconsModal, setShowIconsModal] = useState(false);
  const [showIllustrationsModal, setShowIllustrationsModal] = useState(false);
  const [showMockupsModal, setShowMockupsModal] = useState(false);
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
  const [totalMockups, setTotalMockups] = useState(0);
  const [totalLabels, setTotalLabels] = useState(0);
  const [activeTab, setActiveTab] = useState<"users" | "email">("users");
  const [emailSubject, setEmailSubject] = useState("");
  const [emailBody, setEmailBody] = useState(DEFAULT_EMAIL_BODY);
  const [emailRecipientScope, setEmailRecipientScope] = useState<
    "ME" | "EVERYBODY"
  >("ME");
  const [showEmailConfirmModal, setShowEmailConfirmModal] = useState(false);
  const [emailSending, setEmailSending] = useState(false);
  const [emailStatus, setEmailStatus] = useState<string | null>(null);
  const [emailError, setEmailError] = useState<string | null>(null);

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

  useEffect(() => {
    if (authState.authenticated && authState.user?.isAdmin) {
      const fetchStats = async () => {
        try {
          const response = await fetch(`/api/admin/stats`, {
            credentials: "include",
          });

          if (!response.ok) {
            throw new Error("Failed to fetch stats");
          }
          
          const stats = await response.json();

          setTotalIcons(stats.totalIcons);
          setTotalIllustrations(stats.totalIllustrations);
          setTotalMockups(stats.totalMockups);
          setTotalLabels(stats.totalLabels);
        } catch (err) {
          console.error("Error fetching stats:", err);
          // Silently fail on total counts.
        }
      };

      fetchStats();
    }
  }, [authState.authenticated, authState.user?.isAdmin]);

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
        generatedMockupsCount: "id",
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

  const fetchUserMockups = async (userId: number) => {
    setLoadingMockups(true);
    try {
      const response = await fetch(`/api/admin/users/${userId}/mockups`, {
        credentials: "include",
      });

      if (!response.ok) {
        throw new Error("Failed to fetch user mockups");
      }

      const data: UserMockup[] = await response.json();
      setUserMockups(data);
    } catch (err: any) {
      console.error("Error fetching user mockups:", err);
      setUserMockups([]);
    } finally {
      setLoadingMockups(false);
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

  const handleViewMockups = async (user: UserAdminData) => {
    setSelectedUser(user);
    setShowMockupsModal(true);
    await fetchUserMockups(user.id);
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

  const closeMockupsModal = () => {
    setShowMockupsModal(false);
    setSelectedUser(null);
    setUserMockups([]);
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

  const handleItemsPerPageChange = (value: number) => {
    setItemsPerPage(value);
    setCurrentPage(0);
  };

  const resetEmailStatusMessages = () => {
    setEmailStatus(null);
    setEmailError(null);
  };

  const handleEmailSubjectChange = (value: string) => {
    setEmailSubject(value);
    resetEmailStatusMessages();
  };

  const handleEmailBodyChange = (value: string) => {
    setEmailBody(value);
    resetEmailStatusMessages();
  };

  const handleEmailRecipientScopeChange = (value: "ME" | "EVERYBODY") => {
    setEmailRecipientScope(value);
    resetEmailStatusMessages();
  };

  const handleConfirmSendEmail = () => {
    if (emailSubject.trim().length === 0 || emailBody.trim().length === 0) {
      return;
    }
    resetEmailStatusMessages();
    setShowEmailConfirmModal(true);
  };

  const handleCancelSendEmail = () => {
    setShowEmailConfirmModal(false);
  };

  const resetEmailForm = () => {
    setEmailSubject("");
    setEmailBody(DEFAULT_EMAIL_BODY);
    setEmailRecipientScope("ME");
  };

  const handleSendEmail = async () => {
    setEmailError(null);
    setEmailStatus(null);
    setEmailSending(true);

    try {
      const response = await fetch(`/api/admin/email`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        credentials: "include",
        body: JSON.stringify({
          subject: emailSubject.trim(),
          htmlBody: emailBody,
          recipientScope: emailRecipientScope,
        }),
      });

      if (!response.ok) {
        const errorData = await response
          .json()
          .catch(() => ({ message: "Failed to send email" }));
        throw new Error(errorData.message || "Failed to send email");
      }

      setEmailStatus("Email sent successfully.");
      resetEmailForm();
    } catch (err: any) {
      console.error(err.message);
      setEmailError(err.message);
    } finally {
      setEmailSending(false);
      setShowEmailConfirmModal(false);
    }
  };

  const isEmailFormValid =
    emailSubject.trim().length > 0 && emailBody.trim().length > 0;

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
            Manage users, view system statistics, and send email updates.
          </p>
        </div>

        <div className="bg-white/50 rounded-lg border border-slate-200/80 shadow-lg shadow-slate-200/50 overflow-hidden">
          <div className="flex gap-2 border-b border-slate-200 bg-white/70 px-6 py-3">
            <button
              onClick={() => setActiveTab("users")}
              className={`rounded-md px-4 py-2 text-sm font-semibold transition-colors ${
                activeTab === "users"
                  ? "bg-purple-600 text-white shadow"
                  : "text-slate-600 hover:bg-purple-100"
              }`}
            >
              Users
            </button>
            <button
              onClick={() => setActiveTab("email")}
              className={`rounded-md px-4 py-2 text-sm font-semibold transition-colors ${
                activeTab === "email"
                  ? "bg-purple-600 text-white shadow"
                  : "text-slate-600 hover:bg-purple-100"
              }`}
            >
              Email
            </button>
          </div>

          {activeTab === "users" && (
            <UsersTab
              users={users}
              onSort={handleSort}
              renderSortIcon={renderSortIcon}
              onViewIcons={handleViewIcons}
              onViewIllustrations={handleViewIllustrations}
              onViewMockups={handleViewMockups}
              onOpenSetCoinsModal={handleOpenSetCoinsModal}
              formatDate={formatDate}
              totalElements={totalElements}
              itemsPerPage={itemsPerPage}
              onItemsPerPageChange={handleItemsPerPageChange}
              currentPage={currentPage}
              totalPages={totalPages}
              onPageChange={goToPage}
              totalIcons={totalIcons}
              totalIllustrations={totalIllustrations}
              totalMockups={totalMockups}
              totalLabels={totalLabels}
            />
          )}

          {activeTab === "email" && (
            <EmailTab
              emailSubject={emailSubject}
              emailBody={emailBody}
              emailRecipientScope={emailRecipientScope}
              emailStatus={emailStatus}
              emailError={emailError}
              isEmailFormValid={isEmailFormValid}
              onSubjectChange={handleEmailSubjectChange}
              onBodyChange={handleEmailBodyChange}
              onRecipientChange={handleEmailRecipientScopeChange}
              onRequestSend={handleConfirmSendEmail}
              onReset={resetEmailForm}
            />
          )}
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

      {/* Mockups Modal */}
      {showMockupsModal && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-lg max-w-6xl w-full max-h-[90vh] overflow-hidden flex flex-col">
            <div className="flex items-center justify-between p-6 border-b border-slate-200">
              <div>
                <h2 className="text-2xl font-bold text-slate-800">
                  Mockups for {selectedUser?.email}
                </h2>
                <p className="text-sm text-slate-600 mt-1">
                  Total: {userMockups.length} mockups
                </p>
              </div>
              <button
                onClick={closeMockupsModal}
                className="p-2 hover:bg-slate-100 rounded-lg transition-colors"
              >
                <X className="w-6 h-6 text-slate-600" />
              </button>
            </div>

            <div className="flex-1 overflow-y-auto p-6">
              {loadingMockups ? (
                <div className="flex items-center justify-center py-12">
                  <p className="text-slate-600">Loading mockups...</p>
                </div>
              ) : userMockups.length === 0 ? (
                <div className="flex items-center justify-center py-12">
                  <p className="text-slate-600">
                    No mockups found for this user
                  </p>
                </div>
              ) : (
                <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-4">
                  {userMockups.map((mockup, index) => (
                    <div
                      key={index}
                      className="border rounded-lg p-2 bg-white shadow-sm hover:shadow-md transition-shadow"
                    >
                      <img
                        src={mockup.imageUrl}
                        alt={mockup.description || "Generated Mockup"}
                        className="w-full h-auto object-cover rounded-md"
                      />
                      <div className="mt-2 text-xs text-slate-600 truncate">
                        {mockup.description}
                      </div>
                      <div className="mt-1 text-xs text-slate-400 truncate">
                        {mockup.serviceSource}
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>

            <div className="flex justify-end p-6 border-t border-slate-200">
              <button
                onClick={closeMockupsModal}
                className="px-4 py-2 bg-slate-200 text-slate-700 rounded-lg hover:bg-slate-300 transition-colors"
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}

      {showEmailConfirmModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
          <div className="w-full max-w-md rounded-lg bg-white shadow-lg">
            <div className="border-b border-slate-200 px-6 py-4">
              <h2 className="text-xl font-semibold text-slate-800">
                Confirm email send
              </h2>
            </div>
            <div className="space-y-3 px-6 py-4 text-sm text-slate-600">
              <p>
                You are about to send this message to{" "}
                <span className="font-semibold">
                  {emailRecipientScope === "EVERYBODY"
                    ? "all users"
                    : "yourself"}
                </span>
                .
              </p>
              <p>
                <span className="font-semibold text-slate-700">Subject:</span>{" "}
                {emailSubject || "(no subject)"}
              </p>
              <p>This action cannot be undone. Are you sure you want to proceed?</p>
            </div>
            <div className="flex justify-end gap-2 border-t border-slate-200 px-6 py-4">
              <button
                onClick={handleCancelSendEmail}
                className="rounded-md border border-slate-300 px-4 py-2 text-sm font-semibold text-slate-700 transition-colors hover:bg-slate-100"
                disabled={emailSending}
              >
                Cancel
              </button>
              <button
                onClick={handleSendEmail}
                disabled={emailSending}
                className="rounded-md bg-purple-600 px-4 py-2 text-sm font-semibold text-white shadow-sm transition-colors hover:bg-purple-700 disabled:cursor-not-allowed disabled:opacity-60"
              >
                {emailSending ? "Sending..." : "Send email"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
