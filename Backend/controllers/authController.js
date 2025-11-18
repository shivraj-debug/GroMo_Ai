// Importing the jsonwebtoken package to handle token creation
const jwt = require("jsonwebtoken");

// Importing the User model from Mongoose to interact with the database
const User = require("../models/User");

// Helper function to generate a JWT token using the user ID
const createToken = (id) => {
  return jwt.sign(
    { id }, // Payload — we include user ID in token
    process.env.JWT_SECRET || "my-super-secret-key", // Secret key from env (fallback included)
    {
      expiresIn: "7d", // Token is valid for 7 days
    }
  );
};


// Controller to register a new user
exports.register = async (req, res) => {
  try {
    // Destructuring name, email, password from request body
    const { name, email, password } = req.body;

    // Step 1: Check if user already exists
    const existingUser = await User.findOne({ email });

    if (existingUser) {
      return res.status(400).json({
        success: false,
        message: "Email already in use",
      });
    }

    // Step 2: Create new user
    const user = await User.create({
      name,
      email,
      password,
    });

    // Step 3: Generate JWT token for the new user
    const token = createToken(user._id);

    // Step 4: Return success response with token and user info
    res.status(201).json({
      success: true,
      token,
      user: {
        id: user._id,
        name: user.name,
        email: user.email,
      },
    });
  } catch (error) {
    // Catch unexpected errors
    res.status(500).json({
      success: false,
      message: error.message,
    });
  }
};


// Controller to log in an existing user
exports.login = async (req, res) => {
  try {
    // Extract email and password from request
    const { email, password } = req.body;

    // Step 1: Find user by email
    const user = await User.findOne({ email }).select("+password");
    // Note: Password field is normally excluded, so we explicitly select it

    if (!user) {
      return res.status(401).json({
        success: false,
        message: "Invalid credentials",
      });
    }

    // Step 2: Validate password using model method
    const isPasswordCorrect = await user.correctPassword(password);

    if (!isPasswordCorrect) {
      return res.status(401).json({
        success: false,
        message: "Invalid credentials",
      });
    }

    // Step 3: Generate token if credentials are correct
    const token = createToken(user._id);

    // Step 4: Respond with token and user data
    res.status(200).json({
      success: true,
      token,
      user: {
        id: user._id,
        name: user.name,
        email: user.email,
      },
    });
  } catch (error) {
    // Catch unexpected errors
    res.status(500).json({
      success: false,
      message: error.message,
    });
  }
};
