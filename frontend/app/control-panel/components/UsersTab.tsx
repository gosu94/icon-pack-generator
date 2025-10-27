import { UserAdminData } from "../types";
import { ReactNode } from "react";

interface UsersTabProps {
  users: UserAdminData[];
  onSort: (column: keyof UserAdminData) => void;
  renderSortIcon: (column: keyof UserAdminData) => ReactNode;
  onViewIcons: (user: UserAdminData) => void;
  onViewIllustrations: (user: UserAdminData) => void;
  onViewMockups: (user: UserAdminData) => void;
  onOpenSetCoinsModal: (user: UserAdminData) => void;
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
}

const columnConfig: Array<{
  key: keyof UserAdminData;
  label: string;
}> = [
  { key: "email", label: "Email" },
  { key: "lastLogin", label: "Last Login" },
  { key: "trialCoins", label: "Trial Coins" },
  { key: "coins", label: "Coins" },
  { key: "generatedIconsCount", label: "Generated Icons" },
  { key: "generatedIllustrationsCount", label: "Generated Illustrations" },
  { key: "generatedMockupsCount", label: "Generated Mockups" },
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
  onOpenSetCoinsModal,
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
}: UsersTabProps) {
  return (
    <>
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
                  <button
                    onClick={() => onViewIcons(user)}
                    className="inline-flex items-center px-3 py-1 rounded-md text-sm font-medium bg-gradient-to-r from-blue-600 to-purple-600 text-white hover:from-blue-700 hover:to-purple-700 transition-all duration-200"
                  >
                    View ({user.generatedIconsCount})
                  </button>
                </td>
                <td className="px-6 py-4 whitespace-nowrap text-sm">
                  <button
                    onClick={() => onViewIllustrations(user)}
                    className="inline-flex items-center px-3 py-1 rounded-md text-sm font-medium bg-gradient-to-r from-green-600 to-teal-600 text-white hover:from-green-700 hover:to-teal-700 transition-all duration-200"
                  >
                    View ({user.generatedIllustrationsCount})
                  </button>
                </td>
                <td className="px-6 py-4 whitespace-nowrap text-sm">
                  <button
                    onClick={() => onViewMockups(user)}
                    className="inline-flex items-center px-3 py-1 rounded-md text-sm font-medium bg-gradient-to-r from-pink-500 to-rose-500 text-white hover:from-pink-600 hover:to-rose-600 transition-all duration-200"
                  >
                    View ({user.generatedMockupsCount})
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
                    onClick={() => onOpenSetCoinsModal(user)}
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
