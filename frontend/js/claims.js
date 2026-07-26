const claimUser = () => {
    try {
        return JSON.parse(
            localStorage.getItem("user") || "null"
        );
    } catch (error) {
        return null;
    }
};

function ce(value) {
    return String(value ?? "").replace(
        /[&<>'"]/g,
        character => ({
            "&": "&amp;",
            "<": "&lt;",
            ">": "&gt;",
            "'": "&#39;",
            '"': "&quot;"
        }[character])
    );
}

function getClaimUserId() {
    const user = claimUser();

    if (!user) {
        return "";
    }

    return user.userId || user.id || user._id || "";
}

function getClaimId(claim) {
    if (!claim) {
        return "";
    }

    if (typeof claim._id === "string") {
        return claim._id;
    }

    return claim._id?.$oid || claim.id || "";
}

function showClaimMessage(message, type = "success") {
    const messageBox = document.getElementById("message");

    if (!messageBox) {
        alert(message);
        return;
    }

    messageBox.innerHTML = `
        <div class="message ${ce(type)}">
            ${ce(message)}
        </div>
    `;

    setTimeout(() => {
        messageBox.innerHTML = "";
    }, 5000);
}

/*
 * This function is kept for compatibility with older match pages.
 *
 * The new matches page should preferably use the secure modal instead
 * of calling this function directly.
 */
async function createClaim(
    lostItemId,
    foundItemId,
    ownershipProof = "",
    additionalDetails = "",
    message = ""
) {
    const userId = getClaimUserId();

    if (!userId) {
        window.location.href = "login.html";
        return;
    }

    if (!ownershipProof) {
        ownershipProof = prompt(
            "Enter a unique detail that proves ownership:"
        );

        if (ownershipProof === null) {
            return;
        }

        ownershipProof = ownershipProof.trim();
    }

    if (ownershipProof.length < 10) {
        alert(
            "Please enter at least 10 characters describing a unique ownership detail."
        );
        return;
    }

    try {
        const response = await fetch(
            `${API_BASE_URL}/claims`,
            {
                method: "POST",

                headers: {
                    "Content-Type": "application/json"
                },

                body: JSON.stringify({
                    lostItemId,
                    foundItemId,
                    claimantId: userId,
                    ownershipProof,
                    additionalDetails,
                    message,

                    // Retained for compatibility with the old backend.
                    verificationAnswer: ownershipProof
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

        alert(
            result.message ||
            "Claim submitted successfully"
        );

        window.location.href = "claims.html";

    } catch (error) {
        console.error(error);

        alert(
            error.message ||
            "Unable to submit claim"
        );
    }
}

async function claimAction(id, action) {
    const userId = getClaimUserId();

    if (!userId) {
        window.location.href = "login.html";
        return;
    }

    const actionMessages = {
        APPROVE:
            "Are you sure you want to approve this claim? Contact details will become visible to both users.",

        REJECT:
            "Are you sure you want to reject this claim?",

        RETURNED:
            "Confirm that you have physically returned the item to the owner.",

        RECEIVED:
            "Confirm that you have received the item. This will resolve both posts."
    };

    const confirmationMessage =
        actionMessages[action];

    if (
        confirmationMessage &&
        !window.confirm(confirmationMessage)
    ) {
        return;
    }

    const button = document.querySelector(
        `[data-claim-id="${CSS.escape(id)}"][data-action="${CSS.escape(action)}"]`
    );

    if (button) {
        button.disabled = true;
        button.dataset.originalText =
            button.textContent;
        button.textContent = "Processing...";
    }

    try {
        const response = await fetch(
            `${API_BASE_URL}/claims` +
            `?id=${encodeURIComponent(id)}` +
            `&action=${encodeURIComponent(action)}` +
            `&userId=${encodeURIComponent(userId)}`,
            {
                method: "PUT"
            }
        );

        const result = await response.json();

        if (!response.ok) {
            throw new Error(
                result.message ||
                "Unable to update claim"
            );
        }

        showClaimMessage(
            result.message ||
            "Claim updated successfully"
        );

        await loadClaims();

    } catch (error) {
        console.error(error);

        showClaimMessage(
            error.message ||
            "Unable to update claim",
            "error"
        );

    } finally {
        if (button && document.body.contains(button)) {
            button.disabled = false;
            button.textContent =
                button.dataset.originalText || action;
        }
    }
}

async function loadClaims() {
    const userId = getClaimUserId();
    const box = document.getElementById("claims");

    if (!box) {
        return;
    }

    if (!userId) {
        window.location.href = "login.html";
        return;
    }

    box.innerHTML = `
        <div class="card">
            Loading claims...
        </div>
    `;

    try {
        const response = await fetch(
            `${API_BASE_URL}/claims` +
            `?userId=${encodeURIComponent(userId)}`
        );

        const data = await response.json();

        if (!response.ok) {
            throw new Error(
                data.message ||
                "Unable to load claims"
            );
        }

        if (!Array.isArray(data) || data.length === 0) {
            box.innerHTML = `
                <div class="card empty-state">
                    <h3>No claim requests yet</h3>

                    <p class="muted">
                        Submitted and received claims will appear here.
                    </p>
                </div>
            `;

            return;
        }

        box.innerHTML = data
            .map(claim => createClaimCard(claim))
            .join("");

        highlightSelectedClaim();

    } catch (error) {
        console.error(error);

        box.innerHTML = `
            <div class="message error">
                ${ce(error.message)}
            </div>
        `;
    }
}

function createClaimCard(claim) {
    const claimId = getClaimId(claim);

    const viewerRole = String(
        claim.viewerRole || ""
    ).toUpperCase();

    const status = String(
        claim.status || ""
    ).toUpperCase();

    const isFinder =
        viewerRole === "FINDER";

    const isOwner =
        viewerRole === "OWNER";

    const actions = createClaimActions(
        claimId,
        viewerRole,
        status,
        claim
    );

    const contact = createContactSection(
        claim,
        viewerRole,
        status
    );

    const verification = createVerificationSection(
        claim,
        viewerRole,
        status
    );

    const lostImage = getClaimImageUrl(
        claim.lostItemImage
    );

    const foundImage = getClaimImageUrl(
        claim.foundItemImage
    );

    const statusClass =
        getClaimStatusClass(status);

    const roleLabel =
        isFinder
            ? "You are the finder"
            : "You are the owner";

    return `
        <article
            class="card item claim-card"
            id="claim-${ce(claimId)}"
        >
            <div class="claim-card-header">

                <div>
                    <span class="badge">
                        ${ce(roleLabel)}
                    </span>

                    <span class="badge ${ce(statusClass)}">
                        ${ce(formatClaimStatus(status))}
                    </span>
                </div>

                <span class="claim-id-text">
                    Claim ${ce(claimId.slice(-6))}
                </span>

            </div>

            <div class="claim-items-grid">

                <section class="claim-item-summary">

                    <span class="claim-item-label">
                        Lost report
                    </span>

                    ${
                        lostImage
                            ? `
                                <img
                                    src="${ce(lostImage)}"
                                    alt="${ce(claim.lostItemName || "Lost item")}"
                                    class="claim-item-image"
                                    onerror="this.style.display='none'"
                                >
                            `
                            : ""
                    }

                    <h3>
                        ${ce(claim.lostItemName || "Deleted item")}
                    </h3>

                </section>

                <div class="claim-arrow">
                    &#8596;
                </div>

                <section class="claim-item-summary">

                    <span class="claim-item-label">
                        Found report
                    </span>

                    ${
                        foundImage
                            ? `
                                <img
                                    src="${ce(foundImage)}"
                                    alt="${ce(claim.foundItemName || "Found item")}"
                                    class="claim-item-image"
                                    onerror="this.style.display='none'"
                                >
                            `
                            : ""
                    }

                    <h3>
                        ${ce(claim.foundItemName || "Deleted item")}
                    </h3>

                </section>

            </div>

            ${verification}

            <div class="claim-progress">

                <div class="progress-row">
                    <span>Finder returned item</span>

                    <strong>
                        ${claim.finderReturned ? "Yes" : "No"}
                    </strong>
                </div>

                <div class="progress-row">
                    <span>Owner received item</span>

                    <strong>
                        ${claim.ownerReceived ? "Yes" : "No"}
                    </strong>
                </div>

            </div>

            ${contact}

            ${
                actions
                    ? `
                        <div class="actions claim-actions">
                            ${actions}
                        </div>
                    `
                    : ""
            }

            ${
                status === "REJECTED"
                    ? `
                        <p class="claim-help-text rejected-text">
                            This claim was rejected. The matched items may
                            become available for another valid claim.
                        </p>
                    `
                    : ""
            }

            ${
                status === "RESOLVED"
                    ? `
                        <p class="claim-help-text resolved-text">
                            The owner confirmed receiving the item.
                            This claim is complete.
                        </p>
                    `
                    : ""
            }

        </article>
    `;
}

function createClaimActions(
    claimId,
    viewerRole,
    status,
    claim
) {
    if (
        viewerRole === "FINDER" &&
        status === "PENDING"
    ) {
        return `
            <button
                type="button"
                data-claim-id="${ce(claimId)}"
                data-action="APPROVE"
                onclick="claimAction('${ce(claimId)}', 'APPROVE')"
            >
                Approve Claim
            </button>

            <button
                type="button"
                class="danger"
                data-claim-id="${ce(claimId)}"
                data-action="REJECT"
                onclick="claimAction('${ce(claimId)}', 'REJECT')"
            >
                Reject Claim
            </button>
        `;
    }

    if (
        viewerRole === "FINDER" &&
        status === "APPROVED"
    ) {
        return `
            <button
                type="button"
                data-claim-id="${ce(claimId)}"
                data-action="RETURNED"
                onclick="claimAction('${ce(claimId)}', 'RETURNED')"
            >
                Mark Item Returned
            </button>
        `;
    }

    if (
        viewerRole === "OWNER" &&
        status === "RETURNED" &&
        claim.finderReturned
    ) {
        return `
            <button
                type="button"
                data-claim-id="${ce(claimId)}"
                data-action="RECEIVED"
                onclick="claimAction('${ce(claimId)}', 'RECEIVED')"
            >
                Confirm Item Received
            </button>
        `;
    }

    return "";
}

function createVerificationSection(
    claim,
    viewerRole,
    status
) {
    /*
     * The finder needs the ownership details to evaluate a pending claim.
     * The owner does not need them displayed again while pending.
     */
    if (
        viewerRole !== "FINDER" &&
        status === "PENDING"
    ) {
        return `
            <div class="verification-card">
                <h4>Ownership verification</h4>

                <p class="muted">
                    Your private ownership details were sent to the finder.
                    They are hidden here while the claim is under review.
                </p>
            </div>
        `;
    }

    const ownershipProof =
        claim.ownershipProof ||
        claim.verificationAnswer ||
        "";

    const additionalDetails =
        claim.additionalDetails || "";

    const message =
        claim.message || "";

    if (
        !ownershipProof &&
        !additionalDetails &&
        !message
    ) {
        return "";
    }

    return `
        <section class="verification-card">

            <h4>Ownership verification</h4>

            ${
                ownershipProof
                    ? `
                        <div class="verification-field">
                            <strong>
                                Unique identification details
                            </strong>

                            <p>${ce(ownershipProof)}</p>
                        </div>
                    `
                    : ""
            }

            ${
                additionalDetails
                    ? `
                        <div class="verification-field">
                            <strong>
                                Additional ownership details
                            </strong>

                            <p>${ce(additionalDetails)}</p>
                        </div>
                    `
                    : ""
            }

            ${
                message
                    ? `
                        <div class="verification-field">
                            <strong>
                                Message
                            </strong>

                            <p>${ce(message)}</p>
                        </div>
                    `
                    : ""
            }

        </section>
    `;
}

function createContactSection(
    claim,
    viewerRole,
    status
) {
    const contactVisible =
        claim.contactVisible === true ||
        ["APPROVED", "RETURNED", "RESOLVED"]
            .includes(status);

    if (!contactVisible) {
        return `
            <div class="contact-card contact-hidden">

                <strong>
                    Contact details are protected
                </strong>

                <p>
                    Phone number and email will be shown only after
                    the finder approves the claim.
                </p>

            </div>
        `;
    }

    const name =
        claim.otherPartyName || "-";

    const phone =
        claim.otherPartyPhone || "";

    const email =
        claim.otherPartyEmail || "";

    const contactTitle =
        viewerRole === "FINDER"
            ? "Owner contact"
            : "Finder contact";

    if (!name && !phone && !email) {
        return `
            <div class="contact-card">

                <strong>${ce(contactTitle)}</strong>

                <p>
                    Contact information is unavailable.
                </p>

            </div>
        `;
    }

    return `
        <section class="contact-card">

            <h4>${ce(contactTitle)}</h4>

            <p>
                <b>Name:</b>
                ${ce(name)}
            </p>

            ${
                phone
                    ? `
                        <p>
                            <b>Phone:</b>

                            <a href="tel:${ce(phone)}">
                                ${ce(phone)}
                            </a>
                        </p>
                    `
                    : ""
            }

            ${
                email
                    ? `
                        <p>
                            <b>Email:</b>

                            <a href="mailto:${ce(email)}">
                                ${ce(email)}
                            </a>
                        </p>
                    `
                    : ""
            }

        </section>
    `;
}

function getClaimStatusClass(status) {
    switch (status) {
        case "PENDING":
            return "status-pending";

        case "APPROVED":
            return "status-approved";

        case "RETURNED":
            return "status-returned";

        case "RESOLVED":
            return "status-resolved";

        case "REJECTED":
            return "status-rejected";

        default:
            return "";
    }
}

function formatClaimStatus(status) {
    switch (status) {
        case "PENDING":
            return "Pending Review";

        case "APPROVED":
            return "Approved";

        case "RETURNED":
            return "Item Returned";

        case "RESOLVED":
            return "Resolved";

        case "REJECTED":
            return "Rejected";

        default:
            return status || "Unknown";
    }
}

function getClaimImageUrl(imagePath) {
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

function highlightSelectedClaim() {
    const selectedClaimId =
        localStorage.getItem("selectedClaimId");

    if (!selectedClaimId) {
        return;
    }

    const card = document.getElementById(
        `claim-${selectedClaimId}`
    );

    if (card) {
        card.scrollIntoView({
            behavior: "smooth",
            block: "center"
        });

        card.classList.add(
            "selected-claim-card"
        );

        setTimeout(() => {
            card.classList.remove(
                "selected-claim-card"
            );
        }, 5000);
    }

    localStorage.removeItem(
        "selectedClaimId"
    );
}