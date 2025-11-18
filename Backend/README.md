# GroMo AI Mentor Backend

Backend server for the GroMo AI Mentor application, built with Node.js and Express.

## Setup

1. Clone and navigate to the backend directory:

   ```bash
   git clone https://github.com/anurag03-tech/GroMo-AI-Mentor.git
   cd GroMo-AI-Mentor/Backend
   ```

2. Install dependencies:

   ```bash
   npm install
   ```

3. Create `.env` file with required variables:

   ```
   PORT=5000
   MONGO_URI=your_mongodb_connection_string
   JWT_SECRET=your_jwt_secret
   ```

4. Start MongoDB (local or Atlas)

5. Start the server:

   ```bash
   # Development mode
   npm run dev

   # Production mode
   npm start
   ```
