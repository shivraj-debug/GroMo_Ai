const mongoose = require("mongoose");

const chatMessageSchema = new mongoose.Schema({
  userId: {
    type: mongoose.Schema.Types.ObjectId,
    ref: "User",
    required: true,
  },
  chatName: {
    type: String,
    required: true,
    trim: true,
  },
  // Array of message strings in format "(SENDER TIME) Message content"
  messages: {
    type: [String],
    default: [],
  },
  // Track if terms have been accepted for this chat
  termsAccepted: {
    type: Boolean,
    default: false,
  },
  // When terms were accepted
  acceptedAt: {
    type: Date,
    default: null,
  },
  // When this record was created or updated
  updatedAt: {
    type: Date,
    default: Date.now,
  },
  createdAt: {
    type: Date,
    default: Date.now,
  },
});

// Compound index to efficiently find a specific user's chat
chatMessageSchema.index({ userId: 1, chatName: 1 }, { unique: true });

const ChatMessage = mongoose.model("ChatMessage", chatMessageSchema);

module.exports = ChatMessage;
