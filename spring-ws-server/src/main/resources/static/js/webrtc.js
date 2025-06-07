// WebRTC Configuration
const configuration = {
    iceServers: [
        { urls: 'stun:stun.l.google.com:19302' },
        { urls: 'stun:stun1.l.google.com:19302' }
    ]
};

// DOM Elements
const videoCallModal = document.getElementById('video-call-modal');
const callWithUserSpan = document.getElementById('call-with-user');
const callStatus = document.getElementById('call-status');
const callDuration = document.getElementById('call-duration');
const localVideo = document.getElementById('local-video');
const remoteVideo = document.getElementById('remote-video');
const remoteUserLabel = document.getElementById('remote-user-label');
const toggleAudioBtn = document.getElementById('toggle-audio');
const toggleVideoBtn = document.getElementById('toggle-video');
const toggleScreenBtn = document.getElementById('toggle-screen');
const endCallBtn = document.getElementById('end-call');
const incomingCallDialog = document.getElementById('incoming-call-dialog');
const callerNameSpan = document.getElementById('caller-name');
const acceptCallBtn = document.getElementById('accept-call');
const declineCallBtn = document.getElementById('decline-call');
const callRingtone = document.getElementById('call-ringtone');

// WebRTC State
let peerConnection = null;
let localStream = null;
let remoteStream = null;
let screenStream = null;
let currentCall = {
    callId: null,
    remoteUser: null,
    isInitiator: false,
    startTime: null,
    durationInterval: null,
    hasLocalVideo: true,
    hasLocalAudio: true,
    isScreenSharing: false
};

// Add video call button to user chat elements
function addCallButtonToUserElement(userElement, userId) {
    const callButton = document.createElement('button');
    callButton.className = 'call-button';
    callButton.innerHTML = '<i class="fas fa-video"></i> Call';
    callButton.onclick = () => initiateCall(userId);
    userElement.appendChild(callButton);
}

// Initialize media devices
async function initializeMediaDevices() {
    try {
        localStream = await navigator.mediaDevices.getUserMedia({
            audio: true,
            video: true
        });
        localVideo.srcObject = localStream;
        return true;
    } catch (error) {
        console.error('Error accessing media devices:', error);
        return false;
    }
}

// Create and initialize peer connection
function createPeerConnection() {
    if (peerConnection) {
        console.warn('PeerConnection already exists!');
        return;
    }

    peerConnection = new RTCPeerConnection(configuration);

    // Add local stream
    if (localStream) {
        localStream.getTracks().forEach(track => {
            peerConnection.addTrack(track, localStream);
        });
    }

    // Handle incoming tracks
    peerConnection.ontrack = ({ streams: [stream] }) => {
        console.log('Received remote stream');
        remoteVideo.srcObject = stream;
        remoteStream = stream;
    };

    // Handle ICE candidates
    peerConnection.onicecandidate = event => {
        if (event.candidate) {
            sendWebRTCSignal({
                type: 'ice-candidate',
                to: currentCall.remoteUser,
                payload: event.candidate
            });
        }
    };

    peerConnection.oniceconnectionstatechange = () => {
        console.log('ICE connection state:', peerConnection.iceConnectionState);
        switch (peerConnection.iceConnectionState) {
            case 'connected':
                callStatus.textContent = 'Connected';
                startCallTimer();
                break;
            case 'disconnected':
                callStatus.textContent = 'Disconnected';
                break;
            case 'failed':
                callStatus.textContent = 'Connection failed';
                endCall();
                break;
        }
    };

    return peerConnection;
}

// Send WebRTC signaling message through WebSocket
function sendWebRTCSignal(signal) {
    // Access the socket from the global scope (it's defined in main.js)
    const socket = window.socket;
    if (!socket || socket.readyState !== WebSocket.OPEN) {
        console.error('WebSocket is not connected');
        return;
    }

    signal.from = username;
    signal.timestamp = new Date().toISOString();
    socket.send(JSON.stringify(signal));
}

// Process incoming WebRTC signals
window.handleWebRTCSignal = async function(signal) {
    console.log('Received WebRTC signal:', signal.type, 'from:', signal.from);

    switch (signal.type) {
        case 'call-request':
            handleIncomingCall(signal);
            break;

        case 'call-response':
            handleCallResponse(signal);
            break;

        case 'offer':
            handleOffer(signal);
            break;

        case 'answer':
            handleAnswer(signal);
            break;

        case 'ice-candidate':
            handleIceCandidate(signal);
            break;

        case 'call-end':
            handleRemoteCallEnd(signal);
            break;
    }
}

// Handle incoming call request
function handleIncomingCall(signal) {
    currentCall.callId = signal.callId;
    currentCall.remoteUser = signal.from;
    callerNameSpan.textContent = signal.from;
    incomingCallDialog.style.display = 'block';
    callRingtone.play();
}

// Handle call response (accept/reject)
async function handleCallResponse(signal) {
    if (signal.accepted) {
        console.log('Call accepted by', signal.from);
        await createAndSendOffer();
    } else {
        console.log('Call rejected by', signal.from);
        endCall();
        alert('Call was rejected');
    }
}

// Handle incoming offer
async function handleOffer(signal) {
    if (!peerConnection) {
        await initializeMediaDevices();
        createPeerConnection();
    }

    try {
        await peerConnection.setRemoteDescription(new RTCSessionDescription(signal.payload));
        const answer = await peerConnection.createAnswer();
        await peerConnection.setLocalDescription(answer);

        sendWebRTCSignal({
            type: 'answer',
            to: signal.from,
            payload: answer,
            callId: currentCall.callId
        });
    } catch (error) {
        console.error('Error handling offer:', error);
        endCall();
    }
}

// Handle incoming answer
async function handleAnswer(signal) {
    try {
        await peerConnection.setRemoteDescription(new RTCSessionDescription(signal.payload));
    } catch (error) {
        console.error('Error handling answer:', error);
        endCall();
    }
}

// Handle incoming ICE candidate
async function handleIceCandidate(signal) {
    try {
        if (peerConnection) {
            await peerConnection.addIceCandidate(new RTCIceCandidate(signal.payload));
        }
    } catch (error) {
        console.error('Error handling ICE candidate:', error);
    }
}

// Handle remote call end
function handleRemoteCallEnd(signal) {
    endCall();
    alert(`Call ended by ${signal.from}`);
}

// Initiate a call to another user
// Expose this function globally so it can be called from main.js
window.initiateCall = async function(userId) {
    if (currentCall.callId) {
        alert('You are already in a call!');
        return;
    }

    const success = await initializeMediaDevices();
    if (!success) {
        alert('Failed to access camera/microphone');
        return;
    }

    currentCall.remoteUser = userId;
    currentCall.isInitiator = true;
    callWithUserSpan.textContent = userId;
    remoteUserLabel.textContent = userId;

    sendWebRTCSignal({
        type: 'call-request',
        to: userId
    });
}

// Accept incoming call
async function acceptCall() {
    incomingCallDialog.style.display = 'none';
    callRingtone.pause();
    callRingtone.currentTime = 0;

    const success = await initializeMediaDevices();
    if (!success) {
        alert('Failed to access camera/microphone');
        return;
    }

    videoCallModal.style.display = 'flex';
    callWithUserSpan.textContent = currentCall.remoteUser;
    remoteUserLabel.textContent = currentCall.remoteUser;

    sendWebRTCSignal({
        type: 'call-response',
        to: currentCall.remoteUser,
        accepted: true,
        callId: currentCall.callId
    });
}

// Decline incoming call
function declineCall() {
    incomingCallDialog.style.display = 'none';
    callRingtone.pause();
    callRingtone.currentTime = 0;

    sendWebRTCSignal({
        type: 'call-response',
        to: currentCall.remoteUser,
        accepted: false,
        callId: currentCall.callId
    });

    resetCallState();
}

// Create and send offer to remote peer
async function createAndSendOffer() {
    if (!peerConnection) {
        await initializeMediaDevices();
        createPeerConnection();
    }

    try {
        const offer = await peerConnection.createOffer();
        await peerConnection.setLocalDescription(offer);

        sendWebRTCSignal({
            type: 'offer',
            to: currentCall.remoteUser,
            payload: offer,
            callId: currentCall.callId
        });

        videoCallModal.style.display = 'flex';
    } catch (error) {
        console.error('Error creating offer:', error);
        endCall();
    }
}

// Toggle local audio
function toggleAudio() {
    if (localStream) {
        const audioTrack = localStream.getAudioTracks()[0];
        if (audioTrack) {
            audioTrack.enabled = !audioTrack.enabled;
            currentCall.hasLocalAudio = audioTrack.enabled;
            toggleAudioBtn.innerHTML = audioTrack.enabled ? 
                '<i class="fas fa-microphone"></i>' : 
                '<i class="fas fa-microphone-slash"></i>';
            toggleAudioBtn.classList.toggle('muted', !audioTrack.enabled);
        }
    }
}

// Toggle local video
function toggleVideo() {
    if (localStream) {
        const videoTrack = localStream.getVideoTracks()[0];
        if (videoTrack) {
            videoTrack.enabled = !videoTrack.enabled;
            currentCall.hasLocalVideo = videoTrack.enabled;
            toggleVideoBtn.innerHTML = videoTrack.enabled ? 
                '<i class="fas fa-video"></i>' : 
                '<i class="fas fa-video-slash"></i>';
            toggleVideoBtn.classList.toggle('disabled', !videoTrack.enabled);
        }
    }
}

// Toggle screen sharing
async function toggleScreenSharing() {
    if (!peerConnection) return;

    try {
        if (!currentCall.isScreenSharing) {
            screenStream = await navigator.mediaDevices.getDisplayMedia({ video: true });
            const videoTrack = screenStream.getVideoTracks()[0];

            // Replace video track
            const senders = peerConnection.getSenders();
            const sender = senders.find(s => s.track.kind === 'video');
            await sender.replaceTrack(videoTrack);

            // Update local video
            localVideo.srcObject = screenStream;
            toggleScreenBtn.classList.add('active');
            currentCall.isScreenSharing = true;

            // Handle screen sharing stop
            videoTrack.onended = async () => {
                await stopScreenSharing();
            };
        } else {
            await stopScreenSharing();
        }
    } catch (error) {
        console.error('Error toggling screen share:', error);
    }
}

// Stop screen sharing
async function stopScreenSharing() {
    if (!currentCall.isScreenSharing) return;

    try {
        // Stop screen share tracks
        if (screenStream) {
            screenStream.getTracks().forEach(track => track.stop());
        }

        // Restore camera video track
        const videoTrack = localStream.getVideoTracks()[0];
        const senders = peerConnection.getSenders();
        const sender = senders.find(s => s.track.kind === 'video');
        await sender.replaceTrack(videoTrack);

        // Update local video
        localVideo.srcObject = localStream;
        toggleScreenBtn.classList.remove('active');
        currentCall.isScreenSharing = false;
    } catch (error) {
        console.error('Error stopping screen share:', error);
    }
}

// Start call timer
function startCallTimer() {
    currentCall.startTime = Date.now();
    currentCall.durationInterval = setInterval(() => {
        const duration = Math.floor((Date.now() - currentCall.startTime) / 1000);
        const minutes = Math.floor(duration / 60).toString().padStart(2, '0');
        const seconds = (duration % 60).toString().padStart(2, '0');
        callDuration.textContent = `${minutes}:${seconds}`;
    }, 1000);
}

// End the call
function endCall() {
    // Send end call signal if we're in a call
    if (currentCall.remoteUser) {
        sendWebRTCSignal({
            type: 'call-end',
            to: currentCall.remoteUser,
            callId: currentCall.callId
        });
    }

    // Stop all media tracks
    if (localStream) {
        localStream.getTracks().forEach(track => track.stop());
    }
    if (screenStream) {
        screenStream.getTracks().forEach(track => track.stop());
    }

    // Clean up peer connection
    if (peerConnection) {
        peerConnection.close();
        peerConnection = null;
    }

    // Reset UI
    videoCallModal.style.display = 'none';
    incomingCallDialog.style.display = 'none';
    callRingtone.pause();
    callRingtone.currentTime = 0;

    // Clear call timer
    if (currentCall.durationInterval) {
        clearInterval(currentCall.durationInterval);
    }

    resetCallState();
}

// Reset call state
function resetCallState() {
    currentCall = {
        callId: null,
        remoteUser: null,
        isInitiator: false,
        startTime: null,
        durationInterval: null,
        hasLocalVideo: true,
        hasLocalAudio: true,
        isScreenSharing: false
    };

    // Reset UI elements
    callStatus.textContent = 'Connected';
    callDuration.textContent = '00:00';
    toggleAudioBtn.innerHTML = '<i class="fas fa-microphone"></i>';
    toggleVideoBtn.innerHTML = '<i class="fas fa-video"></i>';
    toggleScreenBtn.classList.remove('active');
}

// Event listeners
toggleAudioBtn.addEventListener('click', toggleAudio);
toggleVideoBtn.addEventListener('click', toggleVideo);
toggleScreenBtn.addEventListener('click', toggleScreenSharing);
endCallBtn.addEventListener('click', endCall);
acceptCallBtn.addEventListener('click', acceptCall);
declineCallBtn.addEventListener('click', declineCall);

// Add call button to user elements
const createUserElement = (function() {
    const originalCreateUserElement = window.createUserElement;
    return function(userId) {
        const userElement = originalCreateUserElement(userId);
        addCallButtonToUserElement(userElement, userId);
        return userElement;
    };
})();

// Handle WebRTC signals in WebSocket message handler
const originalMessageHandler = socket.onmessage;
socket.onmessage = function(event) {
    const data = JSON.parse(event.data);
    if (data.type && ['offer', 'answer', 'ice-candidate', 'call-request', 'call-response', 'call-end'].includes(data.type)) {
        handleWebRTCSignal(data);
    } else {
        originalMessageHandler.call(this, event);
    }
};
