import { UserAdminData } from "../types";
import { ReactNode } from "react";
import { Image as ImageIcon, Paintbrush, Layout, Tag, Search, X } from "lucide-react";

interface UsersTabProps {
  users: UserAdminData[];
  onSort: (column: keyof UserAdminData) => void;
  renderSortIcon: (column: keyof UserAdminData) => ReactNode;
  onViewIcons: (user: UserAdminData) => void;
  onViewIllustrations: (user: UserAdminData) => void;
  onViewMockups: (user: UserAdminData) => void;
  onViewLabels: (user: UserAdminData) => void;
  onOpenSetCoinsModal: (user: UserAdminData) => void;
  onDeleteUser: (user: UserAdminData) => void;
  deletingUserId: number | null;
  formatDate: (dateString: string | null) => string;
  totalElements: number;
  itemsPerPage: number;
  onItemsPerPageChange: (value: number) => void;
  currentPage: number;
  totalPages: number;
  onPageChange: (page: number) => void;
  totalIcons: number;
  totalIllustrations: number;
  totalMockups: number;
  totalLabels: number;
  searchTerm: string;
  activeSearchQuery: string;
  onSearchTermChange: (value: string) => void;
  onClearSearch: () => void;
}

const columnConfig: Array<{
  key: keyof UserAdminData;
  label: string;
}> = [
  { key: "email", label: "Email" },
  { key: "lastLogin", label: "Last Login" },
  { key: "trialCoins", label: "Trial Coins" },
  { key: "coins", label: "Coins" },
  { key: "generatedIconsCount", label: "IC" },
  { key: "generatedIllustrationsCount", label: "IL" },
  { key: "generatedMockupsCount", label: "MC" },
  { key: "generatedLabelsCount", label: "LA" },
  { key: "registeredAt", label: "Registered" },
  { key: "authProvider", label: "Provider" },
];

export default function UsersTab({
  users,
  onSort,
  renderSortIcon,
  onViewIcons,
  onViewIllustrations,
  onViewMockups,
  onViewLabels,
  onOpenSetCoinsModal,
  onDeleteUser,
  deletingUserId,
  formatDate,
  totalElements,
  itemsPerPage,
  onItemsPerPageChange,
  currentPage,
  totalPages,
  onPageChange,
  totalIcons,
  totalIllustrations,
  totalMockups,
  totalLabels,
  searchTerm,
  activeSearchQuery,
  onSearchTermChange,
  onClearSearch,
}: UsersTabProps) {
  return (
    <>
      <div className="px-6 py-4 border-b border-slate-200 bg-white flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div className="relative w-full sm:max-w-xs">
          <Search className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
          <input
            type="text"
            value={searchTerm}
            onChange={(e) => onSearchTermChange(e.target.value)}
            placeholder="Search by email"
            className="w-full pl-9 pr-10 py-2 text-sm border border-slate-300 rounded-md focus:outline-none focus:ring-2 focus:ring-purple-500 focus:border-purple-500"
            aria-label="Search users"
          />
          {searchTerm && (
            <button
              type="button"
              onClick={onClearSearch}
              className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600"
              aria-label="Clear search"
            >
              <X className="w-4 h-4" />
            </button>
          )}
        </div>
        {activeSearchQuery && (
          <p className="text-sm text-slate-500">
            Filtering email by <span className="font-medium text-slate-700">{activeSearchQuery}</span>
          </p>
        )}
      </div>
      <div className="overflow-x-auto">
        <table className="w-full">
          <thead className="bg-purple-50 border-b border-slate-200">
            <tr>
              {columnConfig.map((column) => (
                <th
                  key={column.key}
                  className="px-6 py-3 text-left text-xs font-semibold text-slate-700 uppercase tracking-wider cursor-pointer hover:bg-purple-100 transition-colors"
                  onClick={() => onSort(column.key)}
                >
                  <div className="flex items-center">
                    {column.label}
                    {renderSortIcon(column.key)}
                  </div>
                </th>
              ))}
              <th className="px-6 py-3 text-left text-xs font-semibold text-slate-700 uppercase tracking-wider">
                Actions
              </th>
            </tr>
          </thead>
          <tbody className="bg-white divide-y divide-slate-100">
            {users.map((user) => (
              <tr key={user.id} className="hover:bg-slate-50 transition-colors">
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
                  <div className="flex items-center gap-2">
                    <button
                      onClick={() => onViewIcons(user)}
                      className="inline-flex items-center justify-center p-2 rounded-md bg-gradient-to-r from-blue-600 to-purple-600 text-white hover:from-blue-700 hover:to-purple-700 transition-all duration-200"
                      title={`View ${user.generatedIconsCount} icons`}
                    >
                      <ImageIcon className="w-4 h-4" />
                    </button>
                    <span className="text-xs text-slate-600">{user.generatedIconsCount}</span>
                  </div>
                </td>
                <td className="px-6 py-4 whitespace-nowrap text-sm">
                  <div className="flex items-center gap-2">
                    <button
                      onClick={() => onViewIllustrations(user)}
                      className="inline-flex items-center justify-center p-2 rounded-md bg-gradient-to-r from-green-600 to-teal-600 text-white hover:from-green-700 hover:to-teal-700 transition-all duration-200"
                      title={`View ${user.generatedIllustrationsCount} illustrations`}
                    >
                      <Paintbrush className="w-4 h-4" />
                    </button>
                    <span className="text-xs text-slate-600">{user.generatedIllustrationsCount}</span>
                  </div>
                </td>
                <td className="px-6 py-4 whitespace-nowrap text-sm">
                  <div className="flex items-center gap-2">
                    <button
                      onClick={() => onViewMockups(user)}
                      className="inline-flex items-center justify-center p-2 rounded-md bg-gradient-to-r from-pink-500 to-rose-500 text-white hover:from-pink-600 hover:to-rose-600 transition-all duration-200"
                      title={`View ${user.generatedMockupsCount} mockups`}
                    >
                      <Layout className="w-4 h-4" />
                    </button>
                    <span className="text-xs text-slate-600">{user.generatedMockupsCount}</span>
                  </div>
                </td>
                <td className="px-6 py-4 whitespace-nowrap text-sm">
                  <div className="flex items-center gap-2">
                    <button
                      onClick={() => onViewLabels(user)}
                      className="inline-flex items-center justify-center p-2 rounded-md bg-gradient-to-r from-emerald-500 to-sky-500 text-white hover:from-emerald-600 hover:to-sky-600 transition-all duration-200"
                      title={`View ${user.generatedLabelsCount} labels`}
                    >
                      <Tag className="w-4 h-4" />
                    </button>
                    <span className="text-xs text-slate-600">{user.generatedLabelsCount}</span>
                  </div>
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
                  <div className="flex items-center gap-3">
                    <button
                      onClick={() => onOpenSetCoinsModal(user)}
                      className="text-indigo-600 hover:text-indigo-900"
                    >
                      Set Coins
                    </button>
                    <button
                      onClick={() => onDeleteUser(user)}
                      disabled={deletingUserId === user.id}
                      className="text-rose-600 hover:text-rose-800 disabled:opacity-60 disabled:cursor-not-allowed"
                    >
                      {deletingUserId === user.id ? "Deleting..." : "Delete user"}
                    </button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="px-6 py-4 bg-white border-t border-slate-200 flex items-center justify-between">
        <div className="flex items-center gap-4">
          <div className="text-sm text-slate-600">
            {totalElements > 0 ? (
              <>
                Showing {currentPage * itemsPerPage + 1} to{" "}
                {Math.min((currentPage + 1) * itemsPerPage, totalElements)} of{" "}
                {totalElements} users
              </>
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
              onChange={(e) => onItemsPerPageChange(Number(e.target.value))}
              className="px-2 py-1 border border-slate-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-purple-500"
            >
              {[5, 10, 25, 50, 100].map((size) => (
                <option key={size} value={size}>
                  {size}
                </option>
              ))}
            </select>
          </div>
        </div>

        <div className="flex items-center gap-2">
          <button
            onClick={() => onPageChange(0)}
            disabled={currentPage === 0}
            className="px-3 py-1 text-sm font-medium text-slate-700 bg-white border border-slate-300 rounded-md hover:bg-slate-50 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
          >
            First
          </button>
          <button
            onClick={() => onPageChange(currentPage - 1)}
            disabled={currentPage === 0}
            className="px-3 py-1 text-sm font-medium text-slate-700 bg-white border border-slate-300 rounded-md hover:bg-slate-50 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
          >
            Previous
          </button>

          <div className="flex items-center gap-1">
            {Array.from({ length: Math.min(5, totalPages) }, (_, index) => {
              let pageIndex;
              if (totalPages <= 5) {
                pageIndex = index;
              } else if (currentPage <= 2) {
                pageIndex = index;
              } else if (currentPage >= totalPages - 3) {
                pageIndex = totalPages - 5 + index;
              } else {
                pageIndex = currentPage - 2 + index;
              }

              return (
                <button
                  key={pageIndex}
                  onClick={() => onPageChange(pageIndex)}
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
            onClick={() => onPageChange(currentPage + 1)}
            disabled={currentPage === totalPages - 1}
            className="px-3 py-1 text-sm font-medium text-slate-700 bg-white border border-slate-300 rounded-md hover:bg-slate-50 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
          >
            Next
          </button>
          <button
            onClick={() => onPageChange(totalPages - 1)}
            disabled={currentPage === totalPages - 1}
            className="px-3 py-1 text-sm font-medium text-slate-700 bg-white border border-slate-300 rounded-md hover:bg-slate-50 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
          >
            Last
          </button>
        </div>
      </div>

      <div className="space-y-1 px-6 pb-6 text-sm text-slate-600">
        <p>Total Users: {totalElements}</p>
        <p>Total Icons Generated: {totalIcons}</p>
        <p>Total Illustrations Generated: {totalIllustrations}</p>
        <p>Total Mockups Generated: {totalMockups}</p>
        <p>Total Labels Generated: {totalLabels}</p>
      </div>
    </>
  );
}
