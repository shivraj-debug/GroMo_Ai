const express = require("express");
const router = express.Router();
const {
  checkChatAcceptance,
  storeChatAcceptance,
  storeChatMessages,
  getChatMessages,
  getAcceptedChats,
} = require("../controllers/chatController");
const { isUserAuthenticated } = require("../middleware/authMiddleware");

// Check if a chat has accepted terms (protected)
router.get("/accept/:chatName", isUserAuthenticated, checkChatAcceptance);

// Store chat acceptance (protected)
router.post("/accept", isUserAuthenticated, storeChatAcceptance);

// Store chat messages (protected)
router.post("/messages", isUserAuthenticated, storeChatMessages);

// Get chat messages for a specific chat (protected)
router.get("/messages/:chatName", isUserAuthenticated, getChatMessages);

// Get all chats that have accepted terms (protected)
router.get("/accepted", isUserAuthenticated, getAcceptedChats);

module.exports = router;
