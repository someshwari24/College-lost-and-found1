function showMessage(text, ok = true) {
    const box = document.getElementById("message");

    if (!box) {
        alert(text);
        return;
    }

    box.className = `message ${ok ? "success" : "error"}`;
    box.textContent = text;
}

async function registerUser(event) {
    event.preventDefault();

    const form = event.target;
    const button = form.querySelector('button[type="submit"]');
    const data = Object.fromEntries(new FormData(form));

    if (
        typeof API_BASE_URL === "undefined" ||
        !API_BASE_URL
    ) {
        showMessage(
            "API_BASE_URL is not configured in config.js",
            false
        );
        return;
    }

    if (button) {
        button.disabled = true;
        button.textContent = "Registering...";
    }

    showMessage("Connecting to server...", true);

    try {
        const response = await fetch(
            `${API_BASE_URL}/register`,
            {
                method: "POST",
                headers: {
                    "Content-Type": "application/json"
                },
                body: JSON.stringify(data)
            }
        );

        const text = await response.text();

        let output = {};

        try {
            output = text ? JSON.parse(text) : {};
        } catch {
            throw new Error(
                `Invalid response from backend: ${text}`
            );
        }

        if (!response.ok) {
            throw new Error(
                output.message ||
                `Registration failed with status ${response.status}`
            );
        }

        showMessage(
            output.message || "Registration successful",
            true
        );

        form.reset();

        setTimeout(() => {
            window.location.href = "login.html";
        }, 1000);

    } catch (error) {
        console.error("Registration error:", error);

        showMessage(
            error.message ||
            "Unable to connect to the backend",
            false
        );

    } finally {
        if (button) {
            button.disabled = false;
            button.textContent = "Register";
        }
    }
}

async function loginUser(event) {
    event.preventDefault();

    const form = event.target;
    const button = form.querySelector('button[type="submit"]');
    const data = Object.fromEntries(new FormData(form));

    if (
        typeof API_BASE_URL === "undefined" ||
        !API_BASE_URL
    ) {
        showMessage(
            "API_BASE_URL is not configured in config.js",
            false
        );
        return;
    }

    if (button) {
        button.disabled = true;
        button.textContent = "Logging in...";
    }

    showMessage("Connecting to server...", true);

    try {
        const response = await fetch(
            `${API_BASE_URL}/login`,
            {
                method: "POST",
                headers: {
                    "Content-Type": "application/json"
                },
                body: JSON.stringify(data)
            }
        );

        const text = await response.text();

        let output = {};

        try {
            output = text ? JSON.parse(text) : {};
        } catch {
            throw new Error(
                `Invalid response from backend: ${text}`
            );
        }

        if (!response.ok) {
            throw new Error(
                output.message ||
                `Login failed with status ${response.status}`
            );
        }

        const userId =
            output.userId ||
            output.id ||
            output._id ||
            output.user?.userId ||
            output.user?.id ||
            output.user?._id;

        if (!userId) {
            console.log("Login response:", output);

            throw new Error(
                "Backend response does not contain userId"
            );
        }

        const userData = output.user
            ? {
                ...output.user,
                userId
            }
            : {
                ...output,
                userId
            };

        localStorage.setItem(
            "user",
            JSON.stringify(userData)
        );

        showMessage(
            output.message || "Login successful",
            true
        );

        setTimeout(() => {
            window.location.href =
                "student-dashboard.html";
        }, 500);

    } catch (error) {
        console.error("Login error:", error);

        showMessage(
            error.message ||
            "Unable to connect to the backend",
            false
        );

    } finally {
        if (button) {
            button.disabled = false;
            button.textContent = "Login";
        }
    }
}

function getLoggedInUser() {
    const storedUser = localStorage.getItem("user");

    if (!storedUser) {
        return null;
    }

    try {
        return JSON.parse(storedUser);
    } catch {
        localStorage.removeItem("user");
        return null;
    }
}

function requireLogin() {
    const user = getLoggedInUser();

    if (!user) {
        window.location.href = "login.html";
        return null;
    }

    return user;
}

function logout() {
    localStorage.removeItem("user");
    window.location.href = "login.html";
}
