import { useState, useEffect } from "react";
import {
  getRequestWithPagination,
  patchRequest,
} from "../../components/common/api";
import { useNavigate } from "react-router-dom";
import ConfirmModal from "../../components/common/ConfirmModal";
import formatTime from "../../components/common/formatTime";

interface Booking {
  bookingId: string;
  ownerId?: string;
  stationName?: string;
  startTime?: string;
  endTime?: string;
  createdAt: string | Date;
}

interface PagedResult<T> {
  items: T[];
  totalCount: number;
  totalPages: number;
  currentPage: number;
  pageSize: number;
}

function PendingBookingsPage() {
  const navigate = useNavigate();

  const [pendingBookings, setPendingBookings] = useState<PagedResult<Booking>>({
    items: [],
    totalCount: 0,
    totalPages: 0,
    currentPage: 1,
    pageSize: 10,
  });
  const [loading, setLoading] = useState(true);

  const [showConfirmModal, setShowConfirmModal] = useState(false);
  const [currentAction, setCurrentAction] = useState<
    "approve" | "cancel" | null
  >(null);
  const [actionBookingId, setActionBookingId] = useState<string | null>(null);
  const [actionLoading, setActionLoading] = useState<string | null>(null);

  // Fetch pending bookings with pagination
  const fetchPendingBookings = async (pageNumber = 1, pageSize = 10) => {
    setLoading(true);
    try {
      const response = await getRequestWithPagination<Booking>(
        "/bookings/pending",
        {
          pageNumber,
          pageSize,
        }
      );
      if (response?.data) setPendingBookings(response.data);
    } catch (error) {
      console.error("Error fetching pending bookings:", error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchPendingBookings();
  }, []);

  const handleApproveBooking = (bookingId: string) => {
    setActionBookingId(bookingId);
    setCurrentAction("approve");
    setShowConfirmModal(true);
  };

  const handleCancelBooking = (bookingId: string) => {
    setActionBookingId(bookingId);
    setCurrentAction("cancel");
    setShowConfirmModal(true);
  };

  const confirmAction = async () => {
    if (!actionBookingId || !currentAction) return;

    setActionLoading(actionBookingId);

    try {
      const endpoint =
        currentAction === "approve"
          ? `/bookings/${actionBookingId}/approve`
          : `/bookings/${actionBookingId}/cancel`;

      const response = await patchRequest(endpoint, {});

      if (!response || response.status !== 200) {
        throw new Error(`Failed to ${currentAction} booking`);
      }

      await fetchPendingBookings(
        pendingBookings.currentPage,
        pendingBookings.pageSize
      );
    } catch (error) {
      console.error(`Error ${currentAction} booking:`, error);
    } finally {
      setShowConfirmModal(false);
      setActionLoading(null);
      setCurrentAction(null);
      setActionBookingId(null);
    }
  };

  const cancelAction = () => {
    setShowConfirmModal(false);
    setCurrentAction(null);
    setActionBookingId(null);
  };

  const handlePageChange = (page: number) => {
    fetchPendingBookings(page, pendingBookings.pageSize);
  };

  // Loading Spinner
  const LoadingSpinner = () => (
    <div className="flex justify-center items-center py-20">
      <div className="animate-spin rounded-full h-16 w-16 border-t-4 border-blue-600 border-solid"></div>
    </div>
  );

  return (
    <div className="p-6 space-y-6">
      <div className="flex items-center justify-between">
        <h2 className="text-3xl font-semibold text-gray-800">
          Pending Bookings
        </h2>
        <div className="flex items-center gap-3">
          <span className="text-sm text-gray-500">Total pending</span>
          <span className="inline-flex items-center px-3 py-1 rounded-full bg-gray-100 text-gray-800 font-medium">
            {pendingBookings.totalCount}
          </span>
        </div>
      </div>

      {loading ? (
        <LoadingSpinner />
      ) : pendingBookings.items.length === 0 ? (
        <p className="text-gray-500">No pending bookings available.</p>
      ) : (
        <>
          <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-3">
            {pendingBookings.items.map((booking) => (
              <div
                key={booking.bookingId}
                className="bg-white shadow-lg rounded-lg p-6 hover:shadow-xl transition-all flex flex-col border border-gray-200"
              >
                {/* Booking ID - Full width */}
                <div className="mb-4">
                  <p className="text-lg font-semibold text-gray-700 break-words bg-gray-50 px-3 py-2 rounded-md">
                    {booking.bookingId || "N/A"}
                  </p>
                </div>

                {/* Action Buttons - Full width */}
                <div className="flex gap-2 mb-4">
                  <button
                    onClick={() => handleApproveBooking(booking.bookingId)}
                    disabled={actionLoading === booking.bookingId}
                    className="flex-1 bg-green-600 text-white px-3 py-2 rounded-lg hover:bg-green-700 disabled:bg-green-400 transition-all text-sm font-medium whitespace-nowrap flex items-center justify-center min-h-[42px]"
                  >
                    {actionLoading === booking.bookingId &&
                    currentAction === "approve" ? (
                      <div className="flex items-center justify-center">
                        <div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin mr-2"></div>
                        Approving...
                      </div>
                    ) : (
                      "Approve"
                    )}
                  </button>
                  <button
                    onClick={() => handleCancelBooking(booking.bookingId)}
                    disabled={actionLoading === booking.bookingId}
                    className="flex-1 bg-red-600 text-white px-3 py-2 rounded-lg hover:bg-red-700 disabled:bg-red-400 transition-all text-sm font-medium whitespace-nowrap flex items-center justify-center min-h-[42px]"
                  >
                    {actionLoading === booking.bookingId &&
                    currentAction === "cancel" ? (
                      <div className="flex items-center justify-center">
                        <div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin mr-2"></div>
                        Cancelling...
                      </div>
                    ) : (
                      "Cancel"
                    )}
                  </button>
                </div>

                {/* Booking Details */}
                <div className="space-y-3 text-sm text-gray-600 flex-1 mb-4">
                  <div className="flex justify-between items-start">
                    <span className="text-gray-500 whitespace-nowrap pr-2">
                      Owner Id:
                    </span>
                    <span className="font-semibold text-right break-all">
                      {booking.ownerId || "N/A"}
                    </span>
                  </div>
                  <div className="flex justify-between items-start">
                    <span className="text-gray-500 whitespace-nowrap pr-2">
                      Station:
                    </span>
                    <span className="font-semibold text-right break-all">
                      {booking.stationName || "N/A"}
                    </span>
                  </div>
                  <div className="flex justify-between items-start">
                    <span className="text-gray-500 whitespace-nowrap pr-2">
                      Time Slot:
                    </span>
                    <span className="font-semibold text-right break-all">
                      {booking.startTime && booking.endTime
                        ? `${formatTime(booking.startTime)} - ${formatTime(
                            booking.endTime
                          )}`
                        : "N/A"}
                    </span>
                  </div>
                  <div className="flex justify-between items-start">
                    <span className="text-gray-500 whitespace-nowrap pr-2">
                      Booking Date:
                    </span>
                    <span className="font-semibold text-right break-all">
                      {booking.startTime
                        ? new Date(booking.startTime).toLocaleDateString(
                            "en-US",
                            {
                              timeZone: "Asia/Colombo",
                              year: "numeric",
                              month: "2-digit",
                              day: "2-digit",
                            }
                          )
                        : "N/A"}
                    </span>
                  </div>
                  <div className="flex justify-between items-start">
                    <span className="text-gray-500 whitespace-nowrap pr-2">
                      Created At:
                    </span>
                    <span className="font-semibold text-right break-all">
                      {new Date(booking.createdAt).toLocaleString("en-US", {
                        timeZone: "Asia/Colombo",
                        year: "numeric",
                        month: "2-digit",
                        day: "2-digit",
                        hour: "2-digit",
                        minute: "2-digit",
                        hour12: false,
                      })}
                    </span>
                  </div>
                </div>

                {/* View Details Button */}
                <button
                  onClick={() =>
                    navigate(`/admin/bookings/${booking.bookingId}`)
                  }
                  className="w-full bg-blue-600 text-white px-4 py-3 rounded-lg hover:bg-blue-700 transition-all font-medium mt-auto"
                >
                  View Details
                </button>
              </div>
            ))}
          </div>

          {/* Pagination */}
          {pendingBookings.totalPages > 1 && (
            <div className="flex justify-center mt-6 space-x-2">
              {Array.from(
                { length: pendingBookings.totalPages },
                (_, i) => i + 1
              ).map((page) => (
                <button
                  key={page}
                  onClick={() => handlePageChange(page)}
                  className={`px-3 py-1 rounded-md border ${
                    page === pendingBookings.currentPage
                      ? "bg-blue-600 text-white"
                      : "bg-white text-gray-700"
                  }`}
                >
                  {page}
                </button>
              ))}
            </div>
          )}
        </>
      )}

      {showConfirmModal && (
        <ConfirmModal
          title={
            currentAction === "approve"
              ? "Confirm Approval"
              : "Confirm Cancellation"
          }
          message={
            currentAction === "approve"
              ? "Are you sure you want to approve this booking?"
              : "Are you sure you want to cancel this booking?"
          }
          confirmText={
            currentAction === "approve" ? "Yes, Approve" : "Yes, Cancel"
          }
          confirmColor={currentAction === "approve" ? "green" : "red"}
          onConfirm={confirmAction}
          onCancel={cancelAction}
          loading={actionLoading === actionBookingId}
        />
      )}
    </div>
  );
}

export default PendingBookingsPage;
