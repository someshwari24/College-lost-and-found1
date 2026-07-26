let selectedMatch = null;

function getLoggedInUser() {
    try {
        return JSON.parse(localStorage.getItem("user"));
    } catch (error) {
        return null;
    }
}

function escapeHtml(value) {
    return String(value ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
}

function getDocumentId(document) {
    if (!document) {
        return "";
    }

    if (typeof document._id === "string") {
        return document._id;
    }

    return document._id?.$oid || document.id || "";
}

function getImageUrl(imagePath) {
    if (!imagePath) {
        return "";
    }

    if (
        imagePath.startsWith("http://") ||
        imagePath.startsWith("https://")
    ) {
        return imagePath;
    }

    return `${API_BASE_URL}${imagePath}`;
}

function showMatchMessage(text, type = "success") {
    const messageContainer = document.getElementById("message");

    if (!messageContainer) {
        return;
    }

    messageContainer.innerHTML = `
        <div class="alert alert-${escapeHtml(type)}">
            ${escapeHtml(text)}
        </div>
    `;

    setTimeout(() => {
        messageContainer.innerHTML = "";
    }, 5000);
}

async function loadMatches() {
    const matchesContainer = document.getElementById("matches");
    const user = getLoggedInUser();

    if (!user) {
        window.location.href = "login.html";
        return;
    }

    const userId = user.id || user._id;

    matchesContainer.innerHTML = `
        <div class="empty-state">
            Loading possible matches...
        </div>
    `;

    try {
        const response = await fetch(
            `${API_BASE_URL}/api/matches?userId=${encodeURIComponent(userId)}`
        );

        const result = await response.json();

        if (!response.ok) {
            throw new Error(result.message || "Unable to load matches");
        }

        const matches = Array.isArray(result)
            ? result
            : result.matches || [];

        renderMatches(matches, userId);

    } catch (error) {
        console.error(error);

        matchesContainer.innerHTML = `
            <div class="empty-state">
                <h3>Unable to load matches</h3>
                <p>${escapeHtml(error.message)}</p>
            </div>
        `;
    }
}

function renderMatches(matches, currentUserId) {
    const matchesContainer = document.getElementById("matches");

    if (!matches || matches.length === 0) {
        matchesContainer.innerHTML = `
            <div class="empty-state">
                <h2>No possible matches found</h2>
                <p class="muted">
                    New matches will appear when similar lost and found
                    reports are submitted.
                </p>
            </div>
        `;

        return;
    }

    matchesContainer.innerHTML = matches
        .map(match => createMatchCard(match, currentUserId))
        .join("");
}

function createMatchCard(match, currentUserId) {
    /*
     * Adjust these two lines only when your backend uses different field
     * names.
     */
    const lostItem = match.lostItem || match.lost || {};
    const foundItem = match.foundItem || match.found || match.item || {};

    const lostItemId =
        match.lostItemId ||
        getDocumentId(lostItem);

    const foundItemId =
        match.foundItemId ||
        getDocumentId(foundItem);

    const score = Number(
        match.score ??
        match.matchScore ??
        match.percentage ??
        0
    );

    const item = foundItem.itemName
        ? foundItem
        : lostItem;

    const itemName =
        item.itemName ||
        item.name ||
        "Unnamed item";

    const category =
        item.category ||
        "Not specified";

    const color =
        item.color ||
        "Not specified";

    const brand =
        item.brand ||
        "Not specified";

    const location =
        item.location ||
        "Not specified";

    const eventDate =
        item.eventDate ||
        item.date ||
        "Not specified";

    const description =
        item.description ||
        "No description provided";

    const imagePath =
        foundItem.imagePath ||
        item.imagePath ||
        "";

    const imageUrl = getImageUrl(imagePath);

    const finderName =
        match.finderName ||
        foundItem.userName ||
        foundItem.ownerName ||
        "Finder";

    const finderUserId =
        match.finderId ||
        foundItem.userId ||
        "";

    const claimStatus =
        String(
            match.claimStatus ||
            match.currentUserClaimStatus ||
            ""
        ).toUpperCase();

    const isOwnFoundItem =
        finderUserId &&
        String(finderUserId) === String(currentUserId);

    const matchLevel =
        score >= 70
            ? "STRONG MATCH"
            : "POSSIBLE MATCH";

    const matchLevelClass =
        score >= 70
            ? "match-level-strong"
            : "match-level-possible";

    let actionHtml = "";

    if (isOwnFoundItem) {
        actionHtml = `
            <span class="claim-status">
                You posted this found item
            </span>
        `;
    } else if (claimStatus === "PENDING") {
        actionHtml = `
            <span class="claim-status claim-status-pending">
                Claim submitted — waiting for finder review
            </span>
        `;
    } else if (claimStatus === "APPROVED") {
        actionHtml = `
            <span class="claim-status claim-status-approved">
                Claim approved — open the Claims page to view contact details
            </span>

            <a
                class="claim-button"
                href="claims.html"
                style="text-decoration:none"
            >
                Open Claim
            </a>
        `;
    } else if (claimStatus === "REJECTED") {
        actionHtml = `
            <span class="claim-status claim-status-rejected">
                Your previous claim was rejected
            </span>
        `;
    } else {
        actionHtml = `
            <button
                type="button"
                class="claim-button"
                onclick="openClaimModal(
                    '${escapeHtml(lostItemId)}',
                    '${escapeHtml(foundItemId)}',
                    '${escapeHtml(itemName)}'
                )"
            >
                Claim This Item
            </button>
        `;
    }

    const imageHtml = imageUrl
        ? `
            <img
                class="match-image"
                src="${escapeHtml(imageUrl)}"
                alt="${escapeHtml(itemName)}"
                onerror="this.parentElement.innerHTML='<div class=&quot;no-image&quot;>Image unavailable</div>'"
            >
        `
        : `
            <div class="no-image">
                No image uploaded
            </div>
        `;

    return `
        <article
            class="match-card"
            id="match-${escapeHtml(foundItemId)}"
        >

            <div class="match-card-header">

                <div class="match-title-area">

                    <span class="item-type-badge item-type-found">
                        FOUND ITEM
                    </span>

                    <span class="match-level-badge ${matchLevelClass}">
                        ${matchLevel}
                    </span>

                    <h2>${escapeHtml(itemName)}</h2>

                </div>

                <div class="score-circle">
                    <strong>${score.toFixed(0)}%</strong>
                    <span>Match score</span>
                </div>

            </div>

            <div class="match-content">

                <div class="match-image-wrapper">
                    ${imageHtml}
                </div>

                <div>

                    <div class="match-details">

                        <div class="detail-box">
                            <span class="detail-label">Category</span>
                            <span class="detail-value">
                                ${escapeHtml(category)}
                            </span>
                        </div>

                        <div class="detail-box">
                            <span class="detail-label">Color</span>
                            <span class="detail-value">
                                ${escapeHtml(color)}
                            </span>
                        </div>

                        <div class="detail-box">
                            <span class="detail-label">Brand</span>
                            <span class="detail-value">
                                ${escapeHtml(brand)}
                            </span>
                        </div>

                        <div class="detail-box">
                            <span class="detail-label">Location</span>
                            <span class="detail-value">
                                ${escapeHtml(location)}
                            </span>
                        </div>

                        <div class="detail-box">
                            <span class="detail-label">Found date</span>
                            <span class="detail-value">
                                ${escapeHtml(eventDate)}
                            </span>
                        </div>

                        <div class="detail-box full-width">
                            <span class="detail-label">Description</span>
                            <span class="detail-value">
                                ${escapeHtml(description)}
                            </span>
                        </div>

                    </div>

                    ${
                        score >= 70
                            ? `
                                <div class="match-recommendation">
                                    This item has a strong similarity score.
                                    Verify the details carefully before claiming.
                                </div>
                            `
                            : `
                                <div class="security-box">
                                    <strong>Possible match</strong>
                                    Some details are similar, but you should
                                    verify them before submitting a claim.
                                </div>
                            `
                    }

                    <div class="posted-by-box">

                        <strong>Posted by:</strong>
                        ${escapeHtml(finderName)}

                        <div class="hidden-contact">
                            Phone number and email are hidden until the finder
                            approves your claim.
                        </div>

                    </div>

                    <div class="match-actions">
                        ${actionHtml}
                    </div>

                </div>

            </div>

        </article>
    `;
}

function openClaimModal(lostItemId, foundItemId, itemName) {
    selectedMatch = {
        lostItemId,
        foundItemId,
        itemName
    };

    document.getElementById("claimLostItemId").value =
        lostItemId;

    document.getElementById("claimFoundItemId").value =
        foundItemId;

    document.getElementById("claimForm").reset();

    document.getElementById("claimLostItemId").value =
        lostItemId;

    document.getElementById("claimFoundItemId").value =
        foundItemId;

    document.getElementById("claimModal").classList.add("show");

    document.body.style.overflow = "hidden";

    setTimeout(() => {
        document.getElementById("ownershipProof").focus();
    }, 100);
}

function closeClaimModal() {
    document.getElementById("claimModal").classList.remove("show");

    document.body.style.overflow = "";

    selectedMatch = null;

    document.getElementById("claimForm").reset();
}

function closeClaimModalFromOverlay(event) {
    if (event.target.id === "claimModal") {
        closeClaimModal();
    }
}

async function submitClaim(event) {
    event.preventDefault();

    const user = getLoggedInUser();

    if (!user) {
        window.location.href = "login.html";
        return;
    }

    const claimantId =
        user.id ||
        user._id;

    const lostItemId =
        document.getElementById("claimLostItemId").value.trim();

    const foundItemId =
        document.getElementById("claimFoundItemId").value.trim();

    const ownershipProof =
        document.getElementById("ownershipProof").value.trim();

    const additionalDetails =
        document.getElementById("additionalDetails").value.trim();

    const collectionMessage =
        document.getElementById("collectionMessage").value.trim();

    if (
        !lostItemId ||
        !foundItemId ||
        !ownershipProof ||
        !additionalDetails ||
        !collectionMessage
    ) {
        showMatchMessage(
            "Please complete all claim fields.",
            "danger"
        );

        return;
    }

    if (ownershipProof.length < 10) {
        showMatchMessage(
            "Enter a more detailed unique identification.",
            "danger"
        );

        return;
    }

    const submitButton =
        document.getElementById("submitClaimButton");

    submitButton.disabled = true;
    submitButton.textContent = "Submitting...";

    try {
        const response = await fetch(
            `${"https://college-lost-and-found-2erx.onrender.com"}/api/claims`,
            {
                method: "POST",

                headers: {
                    "Content-Type": "application/json"
                },

                body: JSON.stringify({
                    claimantId,
                    lostItemId,
                    foundItemId,
                    ownershipProof,
                    additionalDetails,
                    message: collectionMessage
                })
            }
        );

        const result = await response.json();

        if (!response.ok) {
            throw new Error(
                result.message ||
                "Unable to submit claim"
            );
        }

        closeClaimModal();

        showMatchMessage(
            "Claim submitted successfully. The finder will review your ownership details."
        );

        await loadMatches();

    } catch (error) {
        console.error(error);

        showMatchMessage(
            error.message,
            "danger"
        );

    } finally {
        submitButton.disabled = false;
        submitButton.textContent = "Submit Claim";
    }
}

document.addEventListener("keydown", event => {
    if (event.key === "Escape") {
        closeClaimModal();
    }
});