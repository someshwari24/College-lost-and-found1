let selectedMatch = null;

 

/* =========================================================

   USER AND BASIC HELPERS

========================================================= */

 

function getLoggedInUser() {

    try {

        const storedUser = localStorage.getItem("user");

 

        if (!storedUser) {

            return null;

        }

 

        return JSON.parse(storedUser);

    } catch (error) {

        localStorage.removeItem("user");

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

 

    if (typeof document === "string") {

        return document;

    }

 

    if (typeof document._id === "string") {

        return document._id;

    }

 

    if (document._id?.$oid) {

        return document._id.$oid;

    }

 

    return (

        document.id ||

        document.itemId ||

        ""

    );

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

 

    const backendBaseUrl =

        API_BASE_URL.replace(/\/api\/?$/, "");

 

    const normalizedPath =

        imagePath.startsWith("/")

            ? imagePath

            : `/${imagePath}`;

 

    return `${backendBaseUrl}${normalizedPath}`;

}

 

async function parseBackendResponse(response) {

    const text = await response.text();

 

    if (!text) {

        return {};

    }

 

    try {

        return JSON.parse(text);

    } catch {

        throw new Error(

            text || "Invalid response from backend"

        );

    }

}

 

function showMatchMessage(

    text,

    type = "success"

) {

    const messageContainer =

        document.getElementById("message");

 

    if (!messageContainer) {

        alert(text);

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

 

/* =========================================================

   LOAD MATCHES

========================================================= */

 

async function loadMatches() {

    const matchesContainer =

        document.getElementById("matches");

 

    if (!matchesContainer) {

        return;

    }

 

    const user = getLoggedInUser();

 

    if (!user) {

        window.location.href = "login.html";

        return;

    }

 

    const userId =

        user.userId ||

        user.id ||

        user._id ||

        "";

 

    if (!userId) {

        localStorage.removeItem("user");

        window.location.href = "login.html";

        return;

    }

 

    const urlParams =

        new URLSearchParams(

            window.location.search

        );

 

    const itemId =

        urlParams.get("itemId");

 

    if (!itemId) {

        matchesContainer.innerHTML = `

            <div class="empty-state">

                <h3>Unable to load matches</h3>

 

                <p>

                    Item ID is missing from the page URL.

                </p>

 

                <a

                    class="btn"

                    href="my-posts.html"

                >

                    Return to My Posts

                </a>

            </div>

        `;

 

        return;

    }

 

    matchesContainer.innerHTML = `

        <div class="empty-state">

            Loading possible matches...

        </div>

    `;

 

    try {

        const response = await fetch(

            `${API_BASE_URL}/matches` +

            `?itemId=${encodeURIComponent(itemId)}` +

            `&userId=${encodeURIComponent(userId)}`

        );

 

        const result =

            await parseBackendResponse(response);

 

        console.log(

            "Matches API Response:",

            result

        );

 

        if (!response.ok) {

            throw new Error(

                result.message ||

                "Unable to load matches"

            );

        }

 

        const matches =

            Array.isArray(result)

                ? result

                : result.matches ||

                  result.data ||

                  [];

 

        renderMatches(

            matches,

            userId,

            itemId

        );

 

    } catch (error) {

        console.error(

            "Load matches error:",

            error

        );

 

        matchesContainer.innerHTML = `

            <div class="empty-state">

                <h3>Unable to load matches</h3>

 

                <p>

                    ${escapeHtml(

                        error.message ||

                        "Something went wrong"

                    )}

                </p>

            </div>

        `;

    }

}

 

/* =========================================================

   RENDER MATCHES

========================================================= */

 

function renderMatches(

    matches,

    currentUserId,

    selectedItemId

) {

    const matchesContainer =

        document.getElementById("matches");

 

    if (!matchesContainer) {

        return;

    }

 

    if (

        !Array.isArray(matches) ||

        matches.length === 0

    ) {

        matchesContainer.innerHTML = `

            <div class="empty-state">

                <h2>No possible matches found</h2>

 

                <p class="muted">

                    New matches will appear when

                    similar lost and found reports

                    are submitted.

                </p>

            </div>

        `;

 

        return;

    }

 

    matchesContainer.innerHTML =

        matches

            .map(match =>

                createMatchCard(

                    match,

                    currentUserId,

                    selectedItemId

                )

            )

            .join("");

}

 

function createMatchCard(

    match,

    currentUserId,

    selectedItemId

) {

    console.log(

        "Individual match object:",

        match

    );

 

    /*

     * Supports multiple possible backend response names.

     */

 

    const sourceItem =

        match.sourceItem ||

        match.requestedItem ||

        match.originalItem ||

        match.lostItem ||

        match.lost ||

        {};

 

    const matchedItem =

        match.matchedItem ||

        match.targetItem ||

        match.foundItem ||

        match.found ||

        match.item ||

        match.match ||

        match;

 

    const sourceType =

        String(

            sourceItem.type ||

            sourceItem.itemType ||

            match.sourceType ||

            ""

        ).toUpperCase();

 

    const matchedType =

        String(

            matchedItem.type ||

            matchedItem.itemType ||

            match.matchedType ||

            ""

        ).toUpperCase();

 

    let lostItem = {};

    let foundItem = {};

 

    if (sourceType === "LOST") {

        lostItem = sourceItem;

    } else if (sourceType === "FOUND") {

        foundItem = sourceItem;

    }

 

    if (matchedType === "LOST") {

        lostItem = matchedItem;

    } else if (matchedType === "FOUND") {

        foundItem = matchedItem;

    }

 

    /*

     * Fallback when backend does not return item type.

     */

 

    if (

        Object.keys(lostItem).length === 0 &&

        match.lostItem

    ) {

        lostItem = match.lostItem;

    }

 

    if (

        Object.keys(foundItem).length === 0 &&

        match.foundItem

    ) {

        foundItem = match.foundItem;

    }

 

    /*

     * The match page should normally display the opposite item.

     * Usually that is the matched item.

     */

 

    const displayItem =

        Object.keys(matchedItem).length > 0

            ? matchedItem

            : Object.keys(foundItem).length > 0

                ? foundItem

                : sourceItem;

 

    /*

     * Resolve IDs using many possible backend response names.

     * Older backend versions return the candidate item directly

     * as the match object, so getDocumentId(match) is included.

     */

 

    const sourceItemId =

        match.sourceItemId ||

        match.requestedItemId ||

        getDocumentId(sourceItem) ||

        selectedItemId ||

        "";

 

    const matchedItemId =

        match.matchedItemId ||

        match.targetItemId ||

        match.itemId ||

        match.candidateItemId ||

        getDocumentId(matchedItem) ||

        getDocumentId(match) ||

        "";

 

    let lostItemId =

        match.lostItemId ||

        match.lostId ||

        getDocumentId(lostItem) ||

        "";

 

    let foundItemId =

        match.foundItemId ||

        match.foundId ||

        getDocumentId(foundItem) ||

        "";

 

    if (sourceType === "LOST") {

        lostItemId = lostItemId || sourceItemId;

        foundItemId = foundItemId || matchedItemId;

    } else if (sourceType === "FOUND") {

        foundItemId = foundItemId || sourceItemId;

        lostItemId = lostItemId || matchedItemId;

    } else if (matchedType === "FOUND") {

        lostItemId = lostItemId || sourceItemId;

        foundItemId = foundItemId || matchedItemId;

    } else if (matchedType === "LOST") {

        foundItemId = foundItemId || sourceItemId;

        lostItemId = lostItemId || matchedItemId;

    } else {

        /*

         * Fallback for an older response shape:

         * the selected URL item is LOST and the returned

         * candidate item is FOUND.

         */

        lostItemId = lostItemId || sourceItemId;

        foundItemId = foundItemId || matchedItemId;

    }

 

    console.log("Resolved claim IDs:", {

        sourceItemId,

        matchedItemId,

        sourceType,

        matchedType,

        lostItemId,

        foundItemId

    });

 

    /*

     * Match score.

     */

 

    let score = Number(

        match.score ??

        match.matchScore ??

        match.similarityScore ??

        match.percentage ??

        0

    );

 

    if (Number.isNaN(score)) {

        score = 0;

    }

 

    if (score >= 0 && score <= 1) {

        score *= 100;

    }

 

    score = Math.min(

        100,

        Math.max(0, score)

    );

 

    /*

     * Item details.

     */

 

    const itemName =

        displayItem.itemName ||

        displayItem.name ||

        displayItem.title ||

        match.itemName ||

        match.matchedItemName ||

        match.name ||

        "Unnamed item";

 

    const category =

        displayItem.category ||

        match.category ||

        match.matchedCategory ||

        "Not specified";

 

    const color =

        displayItem.color ||

        match.color ||

        match.matchedColor ||

        "Not specified";

 

    const brand =

        displayItem.brand ||

        match.brand ||

        match.matchedBrand ||

        "Not specified";

 

    const location =

        displayItem.location ||

        displayItem.foundLocation ||

        displayItem.lostLocation ||

        match.location ||

        match.matchedLocation ||

        "Not specified";

 

    const eventDate =

        displayItem.eventDate ||

        displayItem.date ||

        displayItem.foundDate ||

        displayItem.lostDate ||

        match.eventDate ||

        match.date ||

        "Not specified";

 

    const description =

        displayItem.description ||

        match.description ||

        match.matchedDescription ||

        "No description provided";

 

    const imagePath =

        displayItem.imagePath ||

        displayItem.imageUrl ||

        displayItem.image ||

        displayItem.photo ||

        displayItem.photoUrl ||

        match.imagePath ||

        match.imageUrl ||

        match.image ||

        "";

 

    const imageUrl =

        getImageUrl(imagePath);

 

    /*

     * Finder details.

     */

 

    const finderItem =

        Object.keys(foundItem).length > 0

            ? foundItem

            : matchedType === "FOUND"

                ? matchedItem

                : {};

 

    const finderName =

        match.finderName ||

        finderItem.userName ||

        finderItem.ownerName ||

        finderItem.postedByName ||

        finderItem.studentName ||

        "Finder";

 

    const finderUserId =

        match.finderId ||

        match.foundItemUserId ||

        finderItem.userId ||

        finderItem.ownerId ||

        finderItem.postedBy ||

        "";

 

    const claimStatus =

        String(

            match.claimStatus ||

            match.currentUserClaimStatus ||

            match.status ||

            ""

        ).toUpperCase();

 

    const isOwnFoundItem =

        finderUserId &&

        String(finderUserId) ===

        String(currentUserId);

 

    const matchLevel =

        score >= 70

            ? "STRONG MATCH"

            : "POSSIBLE MATCH";

 

    const matchLevelClass =

        score >= 70

            ? "match-level-strong"

            : "match-level-possible";

 

    /*

     * Claim button/status.

     */

 

    let actionHtml = "";

 

    if (!lostItemId || !foundItemId) {

        actionHtml = `

            <span

                class="claim-status claim-status-rejected"

            >

                Match item information is incomplete

            </span>

        `;

    } else if (isOwnFoundItem) {

        actionHtml = `

            <span class="claim-status">

                You posted this found item

            </span>

        `;

    } else if (claimStatus === "PENDING") {

        actionHtml = `

            <span

                class="claim-status claim-status-pending"

            >

                Claim submitted — waiting for

                finder review

            </span>

        `;

    } else if (claimStatus === "APPROVED") {

        actionHtml = `

            <span

                class="claim-status claim-status-approved"

            >

                Claim approved — open the Claims

                page to view contact details

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

            <span

                class="claim-status claim-status-rejected"

            >

                Your previous claim was rejected

            </span>

        `;

    } else {

        actionHtml = `

            <button

                type="button"

                class="claim-button"

                data-lost-item-id="${escapeHtml(lostItemId)}"

                data-found-item-id="${escapeHtml(foundItemId)}"

                data-item-name="${escapeHtml(itemName)}"

                onclick="openClaimModalFromButton(this)"

            >

                Claim This Item

            </button>

        `;

    }

 

    /*

     * Image.

     */

 

    const imageHtml = imageUrl

        ? `

            <img

                class="match-image"

                src="${escapeHtml(imageUrl)}"

                alt="${escapeHtml(itemName)}"

                onerror="

                    this.parentElement.innerHTML =

                    '<div class=&quot;no-image&quot;>Image unavailable</div>'

                "

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

            id="match-${escapeHtml(

                foundItemId ||

                matchedItemId ||

                ""

            )}"

        >

            <div class="match-card-header">

 

                <div class="match-title-area">

 

                    <span

                        class="item-type-badge item-type-found"

                    >

                        ${matchedType || "MATCHED"} ITEM

                    </span>

 

                    <span

                        class="match-level-badge ${matchLevelClass}"

                    >

                        ${matchLevel}

                    </span>

 

                    <h2>

                        ${escapeHtml(itemName)}

                    </h2>

 

                </div>

 

                <div class="score-circle">

 

                    <strong>

                        ${score.toFixed(0)}%

                    </strong>

 

                    <span>

                        Match score

                    </span>

 

                </div>

 

            </div>

 

            <div class="match-content">

 

                <div class="match-image-wrapper">

                    ${imageHtml}

                </div>

 

                <div>

 

                    <div class="match-details">

 

                        <div class="detail-box">

                            <span class="detail-label">

                                Category

                            </span>

 

                            <span class="detail-value">

                                ${escapeHtml(category)}

                            </span>

                        </div>

 

                        <div class="detail-box">

                            <span class="detail-label">

                                Color

                            </span>

 

                            <span class="detail-value">

                                ${escapeHtml(color)}

                            </span>

                        </div>

 

                        <div class="detail-box">

                            <span class="detail-label">

                                Brand

                            </span>

 

                            <span class="detail-value">

                                ${escapeHtml(brand)}

                            </span>

                        </div>

 

                        <div class="detail-box">

                            <span class="detail-label">

                                Location

                            </span>

 

                            <span class="detail-value">

                                ${escapeHtml(location)}

                            </span>

                        </div>

 

                        <div class="detail-box">

                            <span class="detail-label">

                                Date

                            </span>

 

                            <span class="detail-value">

                                ${escapeHtml(eventDate)}

                            </span>

                        </div>

 

                        <div class="detail-box full-width">

 

                            <span class="detail-label">

                                Description

                            </span>

 

                            <span class="detail-value">

                                ${escapeHtml(description)}

                            </span>

 

                        </div>

 

                    </div>

 

                    ${

                        score >= 70

                            ? `

                                <div class="match-recommendation">

                                    This item has a strong

                                    similarity score. Verify

                                    the details carefully

                                    before claiming.

                                </div>

                            `

                            : `

                                <div class="security-box">

 

                                    <strong>

                                        Possible match

                                    </strong>

 

                                    Some details are similar,

                                    but you should verify them

                                    before submitting a claim.

 

                                </div>

                            `

                    }

 

                    <div class="posted-by-box">

 

                        <strong>

                            Posted by:

                        </strong>

 

                        ${escapeHtml(finderName)}

 

                        <div class="hidden-contact">

                            Phone number and email are hidden

                            until the finder approves your claim.

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

 

/* =========================================================

   CLAIM MODAL

========================================================= */

 

function openClaimModalFromButton(button) {

    const lostItemId =

        button.dataset.lostItemId || "";

 

    const foundItemId =

        button.dataset.foundItemId || "";

 

    const itemName =

        button.dataset.itemName || "";

 

    openClaimModal(

        lostItemId,

        foundItemId,

        itemName

    );

}

 

function openClaimModal(

    lostItemId,

    foundItemId,

    itemName

) {

    if (!lostItemId || !foundItemId) {

        showMatchMessage(

            "Unable to open claim form because item IDs are missing.",

            "danger"

        );

        return;

    }

 

    selectedMatch = {

        lostItemId,

        foundItemId,

        itemName

    };

 

    const claimModal =

        document.getElementById("claimModal");

 

    const claimForm =

        document.getElementById("claimForm");

 

    const lostItemInput =

        document.getElementById(

            "claimLostItemId"

        );

 

    const foundItemInput =

        document.getElementById(

            "claimFoundItemId"

        );

 

    const claimItemName =

        document.getElementById(

            "claimItemName"

        );

 

    if (

        !claimModal ||

        !claimForm ||

        !lostItemInput ||

        !foundItemInput

    ) {

        showMatchMessage(

            "Claim form elements are missing from the page.",

            "danger"

        );

        return;

    }

 

    claimForm.reset();

 

    lostItemInput.value =

        lostItemId;

 

    foundItemInput.value =

        foundItemId;

 

    if (claimItemName) {

        claimItemName.textContent =

            itemName || "Selected item";

    }

 

    claimModal.classList.add("show");

 

    document.body.style.overflow =

        "hidden";

 

    const ownershipProof =

        document.getElementById(

            "ownershipProof"

        );

 

    if (ownershipProof) {

        setTimeout(() => {

            ownershipProof.focus();

        }, 100);

    }

}

 

function closeClaimModal() {

    const claimModal =

        document.getElementById(

            "claimModal"

        );

 

    const claimForm =

        document.getElementById(

            "claimForm"

        );

 

    if (claimModal) {

        claimModal.classList.remove("show");

    }

 

    document.body.style.overflow = "";

 

    selectedMatch = null;

 

    if (claimForm) {

        claimForm.reset();

    }

}

 

function closeClaimModalFromOverlay(event) {

    if (

        event.target &&

        event.target.id === "claimModal"

    ) {

        closeClaimModal();

    }

}

 

/* =========================================================

   SUBMIT CLAIM

========================================================= */

 

async function submitClaim(event) {

    event.preventDefault();

 

    const user = getLoggedInUser();

 

    if (!user) {

        window.location.href = "login.html";

        return;

    }

 

    const claimantId =

        user.userId ||

        user.id ||

        user._id ||

        "";

 

    if (!claimantId) {

        showMatchMessage(

            "User ID is missing. Please log in again.",

            "danger"

        );

 

        localStorage.removeItem("user");

 

        setTimeout(() => {

            window.location.href =

                "login.html";

        }, 1000);

 

        return;

    }

 

    const lostItemInput =

        document.getElementById(

            "claimLostItemId"

        );

 

    const foundItemInput =

        document.getElementById(

            "claimFoundItemId"

        );

 

    const ownershipProofInput =

        document.getElementById(

            "ownershipProof"

        );

 

    const additionalDetailsInput =

        document.getElementById(

            "additionalDetails"

        );

 

    const collectionMessageInput =

        document.getElementById(

            "collectionMessage"

        );

 

    if (

        !lostItemInput ||

        !foundItemInput ||

        !ownershipProofInput ||

        !additionalDetailsInput ||

        !collectionMessageInput

    ) {

        showMatchMessage(

            "Claim form is incomplete.",

            "danger"

        );

        return;

    }

 

    const lostItemId =

        lostItemInput.value.trim();

 

    const foundItemId =

        foundItemInput.value.trim();

 

    const ownershipProof =

        ownershipProofInput.value.trim();

 

    const additionalDetails =

        additionalDetailsInput.value.trim();

 

    const collectionMessage =

        collectionMessageInput.value.trim();

 

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

        document.getElementById(

            "submitClaimButton"

        );

 

    if (submitButton) {

        submitButton.disabled = true;

        submitButton.textContent =

            "Submitting...";

    }

 

    try {

        const response = await fetch(

            `${API_BASE_URL}/claims`,

            {

                method: "POST",

 

                headers: {

                    "Content-Type":

                        "application/json"

                },

 

                body: JSON.stringify({

                    claimantId,

                    lostItemId,

                    foundItemId,

                    ownershipProof,

                    additionalDetails,

                    message:

                        collectionMessage,

 

                    verificationAnswer:

                        ownershipProof

                })

            }

        );

 

        const result =

            await parseBackendResponse(

                response

            );

 

        if (!response.ok) {

            throw new Error(

                result.message ||

                "Unable to submit claim"

            );

        }

 

        closeClaimModal();

 

        showMatchMessage(

            result.message ||

            "Claim submitted successfully. The finder will review your ownership details."

        );

 

        await loadMatches();

 

    } catch (error) {

        console.error(

            "Submit claim error:",

            error

        );

 

        showMatchMessage(

            error.message ||

            "Unable to submit claim",

            "danger"

        );

 

    } finally {

        if (submitButton) {

            submitButton.disabled = false;

            submitButton.textContent =

                "Submit Claim";

        }

    }

}

 

/* =========================================================

   PAGE INITIALIZATION

========================================================= */

 

document.addEventListener(

    "keydown",

    event => {

        if (event.key === "Escape") {

            closeClaimModal();

        }

    }

);

 

document.addEventListener(

    "DOMContentLoaded",

    () => {

        const claimForm =

            document.getElementById(

                "claimForm"

            );

 

        if (claimForm) {

            claimForm.addEventListener(

                "submit",

                submitClaim

            );

        }

 

    }

);