const notificationUser = () => JSON.parse(localStorage.getItem('user') || 'null');
function escapeNotification(value){return String(value??'').replace(/[&<>'"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#39;','"':'&quot;'}[c]));}
function formatNotificationDate(value){if(!value)return '';const d=new Date(value);return Number.isNaN(d.getTime())?value:d.toLocaleString();}

async function loadNotificationCount(){
  const user=notificationUser();
  const badge=document.getElementById('notification-count');
  if(!user||!badge)return;
  try{
    const response=await fetch(`${API_BASE_URL}/notifications?userId=${encodeURIComponent(user.userId)}&unreadOnly=true`);
    const data=await response.json();
    if(!response.ok)return;
    badge.textContent=data.length;
    badge.style.display=data.length?'inline-flex':'none';
  }catch(error){console.error('Unable to load notification count',error);}
}

async function loadNotifications(){
  const user=notificationUser();
  const box=document.getElementById('notifications');
  if(!user||!box)return;
  box.innerHTML='<div class="card">Loading notifications...</div>';
  const response=await fetch(`${API_BASE_URL}/notifications?userId=${encodeURIComponent(user.userId)}`);
  const data=await response.json();
  if(!response.ok){box.innerHTML=`<div class="message error">${escapeNotification(data.message)}</div>`;return;}
  if(!data.length){box.innerHTML='<div class="card">No notifications yet.</div>';return;}
  box.innerHTML=data.map(n=>`<div class="card notification-card ${n.isRead?'':'unread'}" onclick="markNotificationRead('${n._id}', this)">
    <div class="notification-heading"><span class="badge">${escapeNotification(n.type||'UPDATE')}</span>${n.isRead?'':'<span class="new-label">NEW</span>'}</div>
    <p>${escapeNotification(n.message)}</p>
    <p class="muted">${escapeNotification(formatNotificationDate(n.createdAt))}</p>
  </div>`).join('');
}

async function markNotificationRead(id,element){
  const user=notificationUser();
  const response=await fetch(`${API_BASE_URL}/notifications?id=${encodeURIComponent(id)}&userId=${encodeURIComponent(user.userId)}`,{method:'PUT'});
  if(response.ok){element.classList.remove('unread');const label=element.querySelector('.new-label');if(label)label.remove();loadNotificationCount();}
}

async function markAllNotificationsRead(){
  const user=notificationUser();
  const response=await fetch(`${API_BASE_URL}/notifications?action=all&userId=${encodeURIComponent(user.userId)}`,{method:'PUT'});
  const data=await response.json();
  if(!response.ok){alert(data.message);return;}
  await loadNotifications();
  await loadNotificationCount();
}
