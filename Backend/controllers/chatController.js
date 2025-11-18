const ChatMessage = require("../models/ChatMessage");

// Check if a chat has accepted terms
exports.checkChatAcceptance = async (req, res) => {
  try {
    const { chatName } = req.params;

    if (!chatName) {
      return res.status(400).json({
        success: false,
        message: "Chat name is required",
      });
    }

    // Check if terms have been accepted for this chat
    const chatRecord = await ChatMessage.findOne({
      userId: req.user.id,
      chatName: chatName,
    });

    return res.status(200).json({
      success: true,
      accepted: chatRecord ? chatRecord.termsAccepted : false,
    });
  } catch (error) {
    console.error("Error checking chat acceptance:", error);
    return res.status(500).json({
      success: false,
      message: "Server error checking chat acceptance",
    });
  }
};

// Store a new chat acceptance
exports.storeChatAcceptance = async (req, res) => {
  try {
    const { chatName } = req.body;

    if (!chatName) {
      return res.status(400).json({
        success: false,
        message: "Chat name is required",
      });
    }

    // Find or create the chat record
    let chatRecord = await ChatMessage.findOne({
      userId: req.user.id,
      chatName: chatName,
    });

    if (chatRecord) {
      // If record exists but terms not yet accepted
      if (!chatRecord.termsAccepted) {
        chatRecord.termsAccepted = true;
        chatRecord.acceptedAt = new Date();
        chatRecord.updatedAt = new Date();
        await chatRecord.save();
      }
    } else {
      // Create new chat record
      chatRecord = await ChatMessage.create({
        userId: req.user.id,
        chatName: chatName,
        termsAccepted: true,
        acceptedAt: new Date(),
        messages: [], // Initialize with empty messages array
      });
    }

    return res.status(200).json({
      success: true,
      message: "Terms accepted successfully",
      accepted: true,
      data: {
        chatName: chatRecord.chatName,
        acceptedAt: chatRecord.acceptedAt,
      },
    });
  } catch (error) {
    console.error("Error storing chat acceptance:", error);
    return res.status(500).json({
      success: false,
      message: "Server error storing chat acceptance",
    });
  }
};

// Store chat messages
exports.storeChatMessages = async (req, res) => {
  try {
    const { chatName, messages } = req.body;

    if (!chatName || !messages || !Array.isArray(messages)) {
      return res.status(400).json({
        success: false,
        message: "Chat name and valid messages array are required",
      });
    }

    // Find the chat record
    const chatRecord = await ChatMessage.findOne({
      userId: req.user.id,
      chatName: chatName,
    });

    if (!chatRecord) {
      return res.status(404).json({
        success: false,
        message:
          "Chat record not found. Terms must be accepted before storing messages.",
      });
    }

    if (!chatRecord.termsAccepted) {
      return res.status(403).json({
        success: false,
        message: "Terms must be accepted for this chat before storing messages",
      });
    }

    // Track how many new messages we added
    let newMessagesCount = 0;

    // Process each message
    for (const message of messages) {
      // Validate message fields
      if (
        !message.text ||
        !message.timestamp ||
        message.isIncoming === undefined
      ) {
        continue; // Skip invalid messages
      }

      // Format the message as a string: "(SENDER TIME) Message content"
      const sender = message.isIncoming ? chatName : "YOU";
      const formattedMessage = `(${sender} ${message.timestamp}) ${message.text}`;

      // Check if this message already exists to avoid duplicates
      if (!chatRecord.messages.includes(formattedMessage)) {
        // Add message to the END of the messages array
        //chatRecord.messages.push(formattedMessage);
        // Add message to the BEGINNING of the messages array
        chatRecord.messages.unshift(formattedMessage);
        newMessagesCount++;
      }
    }

    // If we added any new messages, save the record
    if (newMessagesCount > 0) {
      chatRecord.updatedAt = new Date();
      await chatRecord.save();
    }

    return res.status(200).json({
      success: true,
      message: `${newMessagesCount} new messages stored successfully`,
      savedCount: newMessagesCount,
    });
  } catch (error) {
    console.error("Error storing chat messages:", error);
    return res.status(500).json({
      success: false,
      message: "Server error storing chat messages",
    });
  }
};

// Get chat messages for a specific chat
exports.getChatMessages = async (req, res) => {
  try {
    const { chatName } = req.params;
    const { limit } = req.query;

    if (!chatName) {
      return res.status(400).json({
        success: false,
        message: "Chat name is required",
      });
    }

    // Find the chat record
    const chatRecord = await ChatMessage.findOne({
      userId: req.user.id,
      chatName: chatName,
    });

    if (!chatRecord) {
      return res.status(404).json({
        success: false,
        message: "Chat not found",
      });
    }

    if (!chatRecord.termsAccepted) {
      return res.status(403).json({
        success: false,
        message:
          "Terms must be accepted for this chat before retrieving messages",
      });
    }

    // Get messages (all if no limit specified)
    const totalMessages = chatRecord.messages.length;
    const startIndex = limit ? Math.max(0, totalMessages - parseInt(limit)) : 0;
    const messages = chatRecord.messages.slice(startIndex);

    // Return the messages as they are in string format, but also convert to objects
    // for compatibility with existing app code
    const messageObjects = messages.map((msgString) => {
      // Extract parts from the formatted string
      // Sample format: "(YOU 4:22 AM) I can help"
      const matches = msgString.match(/\(([^)]+)\) (.+)/);
      if (matches && matches.length === 3) {
        const senderAndTime = matches[1].split(" "); // ["YOU", "4:22", "AM"]
        const sender = senderAndTime[0];
        const timeWithoutSender = senderAndTime.slice(1).join(" "); // "4:22 AM"
        const text = matches[2];

        return {
          text: text,
          timestamp: timeWithoutSender,
          timestampMillis: Date.now(), // This is just an approximation
          isIncoming: sender !== "YOU",
          contactName: chatName,
        };
      }

      // Fallback for messages that don't match expected format
      return {
        text: msgString,
        timestamp: "Unknown",
        timestampMillis: 0,
        isIncoming: false,
        contactName: chatName,
      };
    });

    return res.status(200).json({
      success: true,
      count: messages.length,
      data: messageObjects,
    });
  } catch (error) {
    console.error("Error retrieving chat messages:", error);
    return res.status(500).json({
      success: false,
      message: "Server error retrieving chat messages",
    });
  }
};

// Get all chats that have accepted terms for a user
exports.getAcceptedChats = async (req, res) => {
  try {
    // Find all chat records for this user where terms have been accepted
    const acceptedChats = await ChatMessage.find({
      userId: req.user.id,
      termsAccepted: true,
    }).select("chatName acceptedAt updatedAt");

    // Format the data
    const formattedChats = acceptedChats.map((chat) => ({
      chatName: chat.chatName,
      acceptedAt: chat.acceptedAt,
      lastUpdated: chat.updatedAt,
      messageCount: chat.messages ? chat.messages.length : 0,
    }));

    return res.status(200).json({
      success: true,
      count: formattedChats.length,
      data: formattedChats,
    });
  } catch (error) {
    console.error("Error retrieving accepted chats:", error);
    return res.status(500).json({
      success: false,
      message: "Server error retrieving accepted chats",
    });
  }
};
