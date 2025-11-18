const jwt = require("jsonwebtoken");
const User = require("../models/User");

// Authentication middleware to protect routes

const isUserAuthenticated = async (req, res, next) => {
  try {
    const authHeader = req.headers.authorization;

    if (!authHeader || !authHeader.startsWith("Bearer ")) {
      return res.status(401).json({
        success: false,
        message: "Please login first",
      });
    }

    const token = authHeader.split(" ")[1];
    const data = jwt.verify(
      token,
      process.env.JWT_SECRET || "my-super-secret-key"
    );
    const { id } = data;

    const user = await User.findById(id).select("-password");

    if (!user) {
      return res.status(401).json({
        success: false,
        message: "User not found",
      });
    }

    req.user = user;
    next();
  } catch (error) {
    res.status(401).json({
      success: false,
      message: "Authentication failed",
    });
  }
};

module.exports = { isUserAuthenticated };
