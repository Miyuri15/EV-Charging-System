import { useEffect, useState } from "react";
import { postRequest, putRequest, getRequest } from "../../components/common/api";
import type { CreateStationRequest, Slot, Station } from "../../types";
import { useNavigate, useParams } from "react-router-dom";
import Swal from "sweetalert2";

const StationForm = ({ isEdit = false }: { isEdit?: boolean }) => {
    const [form, setForm] = useState<CreateStationRequest>({
        name: "",
        location: "",
        type: "AC",
        capacity: 0,
        availableSlots: 0,
    });

    const [slots, setSlots] = useState<Slot[]>([]);
    const [deletedSlots, setDeletedSlots] = useState<Slot[]>([]);

    const navigate = useNavigate();
    const { stationId } = useParams();

    // Fetch station in edit mode
    useEffect(() => {
        const fetchStation = async () => {
            if (isEdit && stationId) {
                const res = await getRequest<Station>(`/station/${stationId}`);
                if (res) {
                    setForm({
                        name: res.data.name,
                        location: res.data.location,
                        type: res.data.type,
                        capacity: res.data.capacity,
                        availableSlots: res.data.availableSlots,
                    });
                    setSlots(res.data.slots || []);
                }
            }
        };
        fetchStation();
    }, [isEdit, stationId]);

    const handleChange = (
        e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>
    ) => {
        setForm({ ...form, [e.target.name]: e.target.value });
    };

    // Confirm before changing slot status
    const handleSlotChange = async (index: number, field: keyof Slot, value: string) => {
        const slot = slots[index];

        if (field === "status" && slot.status !== value) {
            const result = await Swal.fire({
                title: "Change Slot Status?",
                text: `Are you sure you want to change slot #${slot.number} status from "${slot.status}" to "${value}"?`,
                icon: "warning",
                showCancelButton: true,
                confirmButtonColor: "#3085d6",
                cancelButtonColor: "#d33",
                confirmButtonText: "Yes, change it",
            });

            if (!result.isConfirmed) return; // stop if cancelled
        }

        // Apply the change
        const updated = [...slots];
        updated[index] = { ...updated[index], [field]: value };
        setSlots(updated);
    };

    // Confirm before deleting slot
    const handleDeleteSlot = async (slotId: string) => {
        const slotToDelete = slots.find((s) => s.slotId === slotId);
        if (!slotToDelete) return;

        const result = await Swal.fire({
            title: "Delete Slot?",
            text: `Are you sure you want to delete slot #${slotToDelete.number}? This action cannot be undone.`,
            icon: "warning",
            showCancelButton: true,
            confirmButtonColor: "#3085d6",
            cancelButtonColor: "#d33",
            confirmButtonText: "Yes, delete it",
        });

        if (!result.isConfirmed) return;

        // Proceed with deletion
        setDeletedSlots([...deletedSlots, slotToDelete]);
        setSlots(slots.filter((s) => s.slotId !== slotId));

        // Optional feedback
        Swal.fire({
            title: "Deleted!",
            text: `Slot #${slotToDelete.number} has been removed.`,
            icon: "success",
            timer: 1500,
            showConfirmButton: false,
        });
    };

    // Add a new slot (local only, will be sent as Add action)
    const handleAddSlot = () => {
        const newSlot: Slot = {
            slotId: `temp-${Date.now()}`, // temporary id for UI
            stationId: stationId || "",
            number: slots.length + 1,
            connectorType: "AC",
            status: "Available",
        };
        setSlots([...slots, newSlot]);
    };

    const handleSubmit = async () => {
        const result = await Swal.fire({
            title: isEdit ? "Update Station?" : "Create Station?",
            text: isEdit
                ? "This will update the station details and slot configurations."
                : "This will create a new charging station.",
            icon: "question",
            showCancelButton: true,
            confirmButtonText: "Yes, proceed",
        });

        if (!result.isConfirmed) return;
        
        if (isEdit && stationId) {
            await putRequest(`/station/${stationId}`, {
                ...form,
                slotUpdates: [
                    // updated slots
                    ...slots
                        .filter((s) => !s.slotId.startsWith("temp-")) // only real slots
                        .map((s) => ({
                            slotId: s.slotId,
                            connectorType: s.connectorType,
                            status: s.status,
                            action: "Update",
                        })),
                    // newly added slots
                    ...slots
                        .filter((s) => s.slotId.startsWith("temp-")) // temp slots
                        .map((s) => ({
                            slotId: "",
                            connectorType: s.connectorType,
                            status: s.status,
                            action: "Add", // 👈 no slotId here
                        })),
                    // deleted slots
                    ...deletedSlots.map((s) => ({
                        slotId: s.slotId,
                        connectorType: s.connectorType,
                        status: s.status,
                        action: "Remove",
                    })),
                ],
            });
        } else {
            await postRequest("/station", form);
        }
        navigate("/admin/stations");
    };


    return (
        <div className="max-w-3xl mx-auto p-6 bg-white shadow-lg rounded-lg mt-20">
            <h2 className="text-2xl font-bold text-gray-800 mb-6">
                {isEdit ? "Edit Station" : "Add New Station"}
            </h2>

            <div className="space-y-5">
                {/* Station Name */}
                <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">
                        Station Name
                    </label>
                    <input
                        name="name"
                        value={form.name}
                        onChange={handleChange}
                        placeholder="Enter station name"
                        className="w-full p-3 border border-gray-300 rounded-md focus:ring-2 focus:ring-green-500 focus:outline-none"
                    />
                </div>

                {/* Location */}
                <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">
                        Location
                    </label>
                    <input
                        name="location"
                        value={form.location}
                        onChange={handleChange}
                        placeholder="Enter location"
                        className="w-full p-3 border border-gray-300 rounded-md focus:ring-2 focus:ring-green-500 focus:outline-none"
                    />
                </div>

                {/* Type */}
                <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">
                        Type
                    </label>
                    <select
                        name="type"
                        value={form.type}
                        onChange={handleChange}
                        className="w-full p-3 border border-gray-300 rounded-md focus:ring-2 focus:ring-green-500 focus:outline-none"
                    >
                        <option value="AC">AC</option>
                        <option value="DC">DC</option>
                    </select>
                </div>

                {/* Capacity (readonly in edit) */}
                <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">
                        Capacity
                    </label>
                    <input
                        type="number"
                        name="capacity"
                        value={form.capacity}
                        readOnly={isEdit}
                        onChange={handleChange}
                        className={`w-full p-3 border rounded-md ${isEdit
                            ? "bg-gray-100 text-gray-500 cursor-not-allowed"
                            : "border-gray-300 focus:ring-2 focus:ring-green-500 focus:outline-none"
                            }`}
                    />
                </div>

                {/* Available Slots (readonly in edit) */}
                {isEdit && (
                    <div>
                        <label className="block text-sm font-medium text-gray-700 mb-1">
                            Available Slots
                        </label>
                        <input
                            type="number"
                            name="availableSlots"
                            value={form.availableSlots ?? ""}
                            readOnly
                            className="w-full p-3 border rounded-md bg-gray-100 text-gray-500 cursor-not-allowed"
                        />
                    </div>
                )}

                {/* Manage Slots (only in edit) */}
                {isEdit && (
                    <div>
                        <div className="flex justify-between items-center mb-3">
                            <h3 className="text-lg font-semibold">Manage Slots</h3>
                            <button
                                type="button"
                                onClick={handleAddSlot}
                                className="px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 transition"
                            >
                                + Add Slot
                            </button>
                        </div>

                        <table className="min-w-full border divide-y divide-gray-200 text-sm">
                            <thead className="bg-gray-100">
                                <tr>
                                    <th className="px-3 py-2 text-left">Slot #</th>
                                    {/* <th className="px-3 py-2 text-left">Connector Type</th> */}
                                    <th className="px-3 py-2 text-left">Status</th>
                                    <th className="px-3 py-2 text-center">Actions</th>
                                </tr>
                            </thead>
                            <tbody>
                                {slots.map((slot, index) => (
                                    <tr key={slot.slotId} className="hover:bg-gray-50">
                                        <td className="px-3 py-2">{slot.number ?? index + 1}</td>
                                        {/* <td className="px-3 py-2">
                                            <select
                                                value={slot.connectorType}
                                                onChange={(e) =>
                                                    handleSlotChange(index, "connectorType", e.target.value)
                                                }
                                                className="p-2 border rounded-md"
                                            >
                                                <option value="AC">AC</option>
                                                <option value="DC">DC</option>
                                            </select>
                                        </td> */}
                                        <td className="px-3 py-2">
                                            <select
                                                value={slot.status}
                                                onChange={(e) =>
                                                    handleSlotChange(index, "status", e.target.value)
                                                }
                                                className="p-2 border rounded-md"
                                            >
                                                <option value="Available">Available</option>
                                                <option value="Under Maintenance">Under Maintenance</option>
                                                <option value="Out Of Order">Out Of Order</option>
                                            </select>
                                        </td>
                                        <td className="px-3 py-2 text-center">
                                            <button
                                                onClick={() => handleDeleteSlot(slot.slotId)}
                                                className="px-3 py-1 bg-red-500 text-white rounded-md hover:bg-red-600 transition"
                                            >
                                                Delete
                                            </button>
                                        </td>
                                    </tr>
                                ))}
                                {slots.length === 0 && (
                                    <tr>
                                        <td
                                            colSpan={4}
                                            className="px-3 py-4 text-center text-gray-500"
                                        >
                                            No slots available
                                        </td>
                                    </tr>
                                )}
                            </tbody>
                        </table>
                    </div>
                )}

                {/* Submit Button */}
                <button
                    onClick={handleSubmit}
                    className="w-full py-3 bg-green-600 text-white font-semibold rounded-md shadow hover:bg-green-700 transition"
                >
                    {isEdit ? "Update Station" : "Create Station"}
                </button>
            </div>
        </div>
    );
};

export default StationForm;
