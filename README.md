# College Lost & Found Portal — Phase 1

## Included
- Student registration and login
- BCrypt password hashing
- Report lost items
- Report found items
- Browse active items
- My posts
- Mark a post resolved
- Delete a post
- MongoDB storage
- Core Java HttpServer backend

## Run locally
1. Install Java 17, Maven and MongoDB.
2. Start MongoDB on `localhost:27017`.
3. Open a terminal inside `backend`.
4. Run: `mvn clean compile exec:java`
5. Open the `frontend` folder using VS Code Live Server.
6. The frontend expects the backend at `http://localhost:8080`.

## MongoDB Atlas
Set environment variables:
- `MONGODB_URI`
- `MONGODB_DB`

## Next phase
- Weighted matching algorithm
- Levenshtein and Jaccard similarity
- Possible matches page
- Claim workflow
- Notifications
- Real image upload

## Phase 2: Intelligent matching

1. Open **My Posts**.
2. Click **Possible Matches** on an active lost or found report.
3. The backend compares it with active opposite-type reports in the same category.
4. Matches scoring at least 50% are shown in descending order.

Weights:
- Category: 30%
- Item name using Levenshtein similarity: 25%
- Color: 15%
- Brand: 10%
- Location: 10%
- Description using Jaccard similarity: 10%

Match levels:
- 80% and above: Strong
- 70% to 79.99%: Possible
- 50% to 69.99%: Weak

## Phase 3 additions
- Claim a matched found item
- Finder approves or rejects ownership claim
- Finder marks item returned
- Owner confirms item received
- Both lost and found posts automatically become RESOLVED
- Claims page shows pending and completed workflows

## Phase 4 additions

- MongoDB `notifications` collection
- Automatic possible-match notifications for scores of 70% or more
- Claim request notifications for finders
- Claim approved/rejected notifications for owners
- Item returned and resolved notifications
- Notifications page with unread styling
- Unread notification count in the navigation bar
- Mark one notification or all notifications as read

### Notification API

```text
GET /api/notifications?userId=USER_ID
GET /api/notifications?userId=USER_ID&unreadOnly=true
PUT /api/notifications?id=NOTIFICATION_ID&userId=USER_ID
PUT /api/notifications?action=all&userId=USER_ID
```

## Phase 6 updates

- Lost and found forms now accept an image file from the student's device.
- The browser sends `multipart/form-data`; image links are no longer required.
- Images are saved by the Java backend in `backend/uploads/item-images` by default.
- Uploaded images are served from `/uploads/<filename>`.
- Maximum image size is 10 MB and any browser-supported `image/*` format is accepted.
- Delete and resolve operations verify that the logged-in student owns the post.
- Posts with active claims cannot be deleted.
- The existing claim workflow remains available from My Posts → Find Matches → Claim Item.

Run the backend from the `backend` directory so the default upload folder is created in the correct location:

```bash
cd backend
mvn clean compile exec:java
```

For deployment, set `UPLOAD_DIR` to a writable persistent folder. Render's normal filesystem is temporary unless a persistent disk or external image service is used.
