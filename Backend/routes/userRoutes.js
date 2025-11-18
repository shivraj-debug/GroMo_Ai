const express = require("express");
const router = express.Router();
const {
  getAllUsers,
  getUserProfile,
} = require("../controllers/userController");
const { isUserAuthenticated } = require("../middleware/authMiddleware");

// Get all users (protected)
router.get("/", isUserAuthenticated, getAllUsers);

// Get current user profile (protected)
router.get("/profile", isUserAuthenticated, getUserProfile);

module.exports = router;
