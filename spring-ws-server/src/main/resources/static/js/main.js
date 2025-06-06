document.addEventListener('DOMContentLoaded', function() {
    // DOM Elements
    const loginScreen = document.getElementById('login-screen');
    const chatScreen = document.getElementById('chat-screen');
    const usernameInput = document.getElementById('username');
    const loginButton = document.getElementById('login-button');
    const messageInput = document.getElementById('message-input');
    const sendButton = document.getElementById('send-button');
    const chatMessages = document.getElementById('chat-messages');
    const usersList = document.getElementById('users-list');
    const currentChatTitle = document.getElementById('current-chat-title');
    const serverId = document.getElementById('server-id');
    const connectionCount = document.getElementById('connection-count');
    
    // WebSocket connection
    let socket = null;
    let username = '';
    let currentClientId = '';
    let currentServerId = '';
    let activeChat = 'public'; // Default to public chat
    let conversations = {
        'public': []
    }; // Store messages by conversation
    
    // Connect to WebSocket server
    function connect() {
        username = usernameInput.value.trim();
        
        if (!username) {
            alert('Please enter a username');
            return;
        }
        
        // Create WebSocket connection with user ID as parameter
        const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        const host = window.location.host;
        socket = new WebSocket(`${protocol}//${host}/ws?X-Auth-User-Id=${encodeURIComponent(username)}`);
        
        // Store the actual username without encoding for display purposes
        window.displayUsername = username;
        
        // Connection opened
        socket.addEventListener('open', function(event) {
            console.log('Connected to WebSocket server');
            
            // Switch to chat screen
            loginScreen.style.display = 'none';
            chatScreen.style.display = 'flex';
            
            // Focus message input
            messageInput.focus();
        });
        
        // Listen for messages
        socket.addEventListener('message', function(event) {
            const data = JSON.parse(event.data);
            console.log('Message from server:', data);
            
            switch(data.type) {
                case 'info':
                    // Store client and server IDs
                    currentClientId = data.clientId;
                    currentServerId = data.serverId;
                    
                    // Update server info
                    serverId.textContent = `Server: ${data.serverId}`;
                    
                    // Update connection count from various possible sources
                    let clients = 0;
                    if (data.additionalData && data.additionalData.clients) {
                        clients = data.additionalData.clients;
                    } else if (data.clients) {
                        clients = data.clients;
                    }
                    connectionCount.textContent = `Connections: ${clients}`;
                    console.log('Updated connections count:', clients);
                    
                    // Display welcome message
                    addInfoMessage(data.message);
                    
                    // Update users list if available
                    if (data.additionalData && data.additionalData.connectedUsers) {
                        console.log('Received connected users from additionalData:', data.additionalData.connectedUsers);
                        updateUsersList(data.additionalData.connectedUsers);
                    } else if (data.connectedUsers) {
                        console.log('Received connected users directly:', data.connectedUsers);
                        updateUsersList(data.connectedUsers);
                    } else {
                        console.log('No connected users found in message:', data);
                    }
                    break;
                    
                case 'status':
                    // Update connection count from various possible sources
                    let statusClients = 0;
                    if (data.additionalData && data.additionalData.clients) {
                        statusClients = data.additionalData.clients;
                    } else if (data.clients) {
                        statusClients = data.clients;
                    }
                    connectionCount.textContent = `Connections: ${statusClients}`;
                    console.log('Updated connections count from status:', statusClients);
                    break;
                    
                case 'user-joined':
                    addInfoMessage(`${data.userId} joined the chat`);
                    // Increment connection count when user joins
                    let currentCount = parseInt(connectionCount.textContent.split(':')[1].trim()) || 0;
                    connectionCount.textContent = `Connections: ${currentCount + 1}`;
                    console.log('User joined, incrementing count to:', currentCount + 1);
                    break;
                    
                case 'user-left':
                    addInfoMessage(`${data.userId} left the chat`);
                    // Decrement connection count when user leaves
                    let currentLeftCount = parseInt(connectionCount.textContent.split(':')[1].trim()) || 0;
                    if (currentLeftCount > 0) {
                        connectionCount.textContent = `Connections: ${currentLeftCount - 1}`;
                        console.log('User left, decrementing count to:', currentLeftCount - 1);
                    }
                    break;
                    
                case 'user-list':
                    console.log('Received user-list update:', JSON.stringify(data));
                    
                    // Get the user list from the message
                    let userList = null;
                    
                    // First check additionalData (most reliable source)
                    if (data.additionalData && data.additionalData.users) {
                        console.log('Found users in additionalData:', data.additionalData.users);
                        userList = data.additionalData.users;
                    } 
                    // Then try top-level users array
                    else if (data.users) {
                        console.log('Found users directly in message:', data.users);
                        userList = data.users;
                    }
                    
                    console.log('Extracted user list:', userList);
                    
                    // Check if usersList element exists
                    if (!usersList) {
                        console.error('Users list element not found in DOM!');
                        usersList = document.getElementById('users-list');
                        if (!usersList) {
                            console.error('Still cannot find users-list element, cannot update UI!');
                            return;
                        }
                    }
                    
                    // Safety check on userList
                    if (!userList) {
                        console.error('No user list found in message!');
                        return;
                    }
                    
                    // Make sure it's an array
                    if (!Array.isArray(userList)) {
                        console.error('User list is not an array:', userList);
                        return;
                    }
                    
                    console.log('Updating users list in UI with:', userList);
                    
                    // Remove all existing user chat options
                    while (usersList.firstChild) {
                        usersList.removeChild(usersList.firstChild);
                    }
                    
                    // Add each user to the list (excluding current user)
                    userList.forEach(user => {
                        console.log('Processing user:', user, 'Current username:', username);
                        if (user !== username) {
                            console.log('Adding user to UI:', user);
                            const userElement = createUserElement(user);
                            usersList.appendChild(userElement);
                        }
                    });
                    
                    console.log('Users list update complete. Users in DOM:', usersList.childElementCount);
                    break;
                    
                case 'error':
                    addInfoMessage(`Error: ${data.message}`);
                    break;
                    
                case 'sent':
                    // Optional: Add visual feedback that message was sent
                    break;
                    
                default:
                    // Handle regular chat message
                    if (data.message) {
                        const isSelf = data.userId === username;
                        addChatMessage(data.message, data.userId, data.timestamp, isSelf);
                    }
            }
        });
        
        // Connection closed
        socket.addEventListener('close', function(event) {
            addInfoMessage('Disconnected from server');
            console.log('Disconnected from WebSocket server');
        });
        
        // Connection error
        socket.addEventListener('error', function(event) {
            addInfoMessage('Connection error');
            console.error('WebSocket error:', event);
        });
    }
    
    // Send message to server
    function sendMessage() {
        const message = messageInput.value.trim();
        
        if (!message || !socket || socket.readyState !== WebSocket.OPEN) {
            return;
        }
        
        const messageData = {
            type: 'chat',
            message: message
        };
        
        // Add recipient if direct message (not public chat)
        if (activeChat !== 'public') {
            messageData.recipientId = activeChat;
        }
        
        socket.send(JSON.stringify(messageData));
        
        // Add message to local conversation immediately for better UX
        const timestamp = new Date().toISOString();
        addChatMessage(message, window.displayUsername || username, timestamp, true, activeChat);
        
        // Clear the input field immediately after sending
        messageInput.value = '';
        messageInput.focus();
    }
    
    // Add chat message to the UI
    function addChatMessage(message, sender, timestamp, isSelf, chatId = null) {
        // Determine which conversation this belongs to
        let targetChatId = chatId;
        
        // If chatId not specified, determine based on sender
        if (!targetChatId) {
            // If message is from current user, it goes to activeChat
            if (isSelf) {
                targetChatId = activeChat;
            } else {
                // Messages from others either go to public chat or a DM chat
                targetChatId = (activeChat === 'public' || sender === activeChat) ? 
                    activeChat : sender;
            }
        }
        
        // Create message element
        const messageDiv = document.createElement('div');
        messageDiv.className = `message ${isSelf ? 'message-self' : 'message-other'}`;
        
        const senderDiv = document.createElement('div');
        senderDiv.className = 'sender';
        senderDiv.textContent = sender;
        
        const contentDiv = document.createElement('div');
        contentDiv.className = 'content';
        contentDiv.textContent = message;
        
        const timeDiv = document.createElement('div');
        timeDiv.className = 'time';
        
        // Format timestamp
        let formattedTime = 'now';
        if (timestamp) {
            const date = new Date(timestamp);
            formattedTime = date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
        }
        
        timeDiv.textContent = formattedTime;
        
        messageDiv.appendChild(senderDiv);
        messageDiv.appendChild(contentDiv);
        messageDiv.appendChild(timeDiv);
        
        // Initialize conversation if it doesn't exist yet
        if (!conversations[targetChatId]) {
            conversations[targetChatId] = [];
            
            // If this is a new user conversation, add it to the UI
            if (targetChatId !== 'public' && targetChatId !== username) {
                addUserChatOption(targetChatId);
            }
        }
        
        // Add message to conversation store
        conversations[targetChatId].push({
            element: messageDiv,
            sender: sender,
            message: message,
            timestamp: timestamp,
            isSelf: isSelf
        });
        
        // If this is the active chat, add message to the UI
        if (targetChatId === activeChat) {
            chatMessages.appendChild(messageDiv);
            // Scroll to bottom
            chatMessages.scrollTop = chatMessages.scrollHeight;
        } else {
            // If not active chat, show notification on the chat option
            showUnreadNotification(targetChatId);
        }
    }
    
    // Add info message to the UI
    function addInfoMessage(message, chatId = 'public') {
        const messageDiv = document.createElement('div');
        messageDiv.className = 'message message-info';
        messageDiv.textContent = message;
        
        // Initialize conversation if it doesn't exist yet
        if (!conversations[chatId]) {
            conversations[chatId] = [];
        }
        
        // Store info message in conversation
        conversations[chatId].push({
            element: messageDiv,
            isInfo: true,
            message: message
        });
        
        // Only add to DOM if this is the active chat
        if (chatId === activeChat) {
            chatMessages.appendChild(messageDiv);
            // Scroll to bottom
            chatMessages.scrollTop = chatMessages.scrollHeight;
        }
    }
    
    // Switch to a specific chat conversation
    function switchToChat(chatId) {
        console.log('Switching to chat:', chatId);
        
        // Remove active class from all chat options
        document.querySelectorAll('.chat-option, .user-chat-option').forEach(el => {
            el.classList.remove('active');
        });
        
        // Add active class to selected chat option
        let selectedOption = document.querySelector(`[data-chat="${chatId}"]`);
        if (!selectedOption && chatId === 'public') {
            // Ensure public chat option is available
            const chatSelection = document.querySelector('.chat-selection');
            if (chatSelection && !document.querySelector('[data-chat="public"]')) {
                const publicChatOption = document.createElement('div');
                publicChatOption.className = 'chat-option';
                publicChatOption.setAttribute('data-chat', 'public');
                publicChatOption.innerHTML = '<i class="fas fa-users"></i><span>Public Chat</span>';
                publicChatOption.addEventListener('click', () => switchToChat('public'));
                chatSelection.appendChild(publicChatOption);
                selectedOption = publicChatOption;
            }
        }
        
        if (selectedOption) {
            selectedOption.classList.add('active');
            
            // Remove notification indicator if present
            const notification = selectedOption.querySelector('.notification');
            if (notification) {
                selectedOption.removeChild(notification);
            }
        } else {
            console.warn('Could not find chat option for:', chatId);
        }
        
        // Update active chat
        activeChat = chatId;
        
        // Update chat title
        if (chatId === 'public') {
            currentChatTitle.textContent = 'Public Chat';
        } else {
            // Decode URI encoded usernames for display
            try {
                currentChatTitle.textContent = decodeURIComponent(chatId);
            } catch (e) {
                currentChatTitle.textContent = chatId; // Fallback if decoding fails
            }
        }
        
        // Clear chat messages
        chatMessages.innerHTML = '';
        
        // Add messages from this conversation
        if (conversations[chatId]) {
            conversations[chatId].forEach(msg => {
                chatMessages.appendChild(msg.element);
            });
        }
        
        // Scroll to bottom
        chatMessages.scrollTop = chatMessages.scrollHeight;
        
        // Focus message input
        messageInput.focus();
    }
    
    // Show unread notification on a chat option
    function showUnreadNotification(chatId) {
        if (chatId === activeChat) return;
        
        const chatOption = document.querySelector(`[data-chat="${chatId}"]`);
        if (!chatOption) return;
        
        // Check if notification already exists
        let notification = chatOption.querySelector('.notification');
        
        if (!notification) {
            // Create new notification
            notification = document.createElement('div');
            notification.className = 'notification';
            notification.textContent = '1';
            chatOption.appendChild(notification);
        } else {
            // Increment existing notification
            let count = parseInt(notification.textContent) || 0;
            notification.textContent = count + 1;
        }
    }
    
    // Create a user chat option in the sidebar
    function addUserChatOption(userId) {
        // Don't add for current user
        if (userId === username) return;
        
        // Check if already exists
        if (document.querySelector(`[data-chat="${userId}"]`)) return;
        
        const userChatOption = document.createElement('div');
        userChatOption.className = 'user-chat-option';
        userChatOption.setAttribute('data-chat', userId);
        
        const avatar = document.createElement('div');
        avatar.className = 'avatar';
        avatar.textContent = userId.charAt(0).toUpperCase();
        
    }
    
    // Create a user element for the sidebar
    function createUserElement(userId) {
        console.log('Creating user element for:', userId);
        
        // Create the main container
        const userElement = document.createElement('div');
        userElement.className = 'user-chat-option';
        userElement.setAttribute('data-chat', userId);
        
        // Create the avatar element
        const avatar = document.createElement('div');
        avatar.className = 'avatar';
        avatar.textContent = userId.charAt(0).toUpperCase();
        
        // Create the user info container
        const userInfo = document.createElement('div');
        userInfo.className = 'user-info';
        
        // Create the username element - decode URI encoded usernames
        const usernameElement = document.createElement('div');
        usernameElement.className = 'username';
        try {
            usernameElement.textContent = decodeURIComponent(userId);
        } catch (e) {
            usernameElement.textContent = userId; // Fallback if decoding fails
        }
        
        // Create the status element
        const statusElement = document.createElement('div');
        statusElement.className = 'status';
        statusElement.textContent = 'Online';
        
        // Assemble the elements
        userInfo.appendChild(usernameElement);
        userInfo.appendChild(statusElement);
        
        userElement.appendChild(avatar);
        userElement.appendChild(userInfo);
        
        // Add click event to switch chat
        userElement.addEventListener('click', function() {
            switchToChat(userId);
        });
        
        console.log('Created user element:', userElement);
        return userElement;
    }
    
    // Clear all users from the sidebar
    function clearUsersList() {
        console.log('Clearing users list');
        // Remove all user chat options from the users list
        const userChatOptions = document.querySelectorAll('.user-chat-option');
        userChatOptions.forEach(el => {
            usersList.removeChild(el);
        });
        console.log('Users list cleared');
    }

    // Update the users list in the UI
    function updateUsersList(users) {
        console.log('Updating users list with:', users);
        
        // Safety check - make sure users is an array
        if (!Array.isArray(users)) {
            console.error('Expected users to be an array, got:', typeof users, users);
            return;
        }
        
        // Clear existing users first
        clearUsersList();
        
        // Add each user to the list (excluding current user)
        users.forEach(user => {
            if (user !== username) {
                addUserChatOption(user);
                console.log('Added user to chat options:', user);
            }
        });
        
        // Log the final state
        console.log('Final user chat options:', document.querySelectorAll('.user-chat-option').length);
    }
    
    // Initialize the chat with event listeners
    function initChat() {
        // Ensure public chat option exists
        const publicChatOption = document.querySelector('.chat-option[data-chat="public"]');
        if (publicChatOption) {
            // Add click event to public chat option
            publicChatOption.addEventListener('click', function() {
                switchToChat('public');
            });
        } else {
            // Create public chat option if it doesn't exist
            const chatSelection = document.querySelector('.chat-selection');
            if (chatSelection) {
                const newPublicChatOption = document.createElement('div');
                newPublicChatOption.className = 'chat-option active';
                newPublicChatOption.setAttribute('data-chat', 'public');
                newPublicChatOption.innerHTML = '<i class="fas fa-users"></i><span>Public Chat</span>';
                newPublicChatOption.addEventListener('click', () => switchToChat('public'));
                
                // Clear and add the public chat option
                chatSelection.innerHTML = '';
                chatSelection.appendChild(newPublicChatOption);
            }
        }
        
        // Always start with public chat active
        switchToChat('public');
    }
    
    // Event listeners
    loginButton.addEventListener('click', connect);
    usernameInput.addEventListener('keypress', function(e) {
        if (e.key === 'Enter') {
            connect();
        }
    });
    
    sendButton.addEventListener('click', sendMessage);
    messageInput.addEventListener('keypress', function(e) {
        if (e.key === 'Enter') {
            e.preventDefault(); // Prevent default behavior
            sendMessage();
        }
    });
    
    // Initialize chat UI on successful connection
    socket && socket.addEventListener('open', initChat);
});
