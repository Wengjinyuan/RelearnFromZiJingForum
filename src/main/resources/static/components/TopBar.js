// components/TopBar.js
// 顶部导航栏组件
Vue.component('top-bar', {
    template: `
        <div class="topbar">
            <div class="wrap">
                <div class="brand" @click="returnIndex">
                    <div class="logo-mini">
                        <svg version="1.0" width="44" height="44" viewBox="0 0 800 800"
                            preserveAspectRatio="xMidYMid meet">
                            <g transform="translate(143.089938,679.550659) scale(0.066804,-0.066804)" fill="#ffffff"
                                stroke="none">
                                <path
                                    d="M3705 7319 c-81 -12 -246 -64 -320 -101 -109 -56 -199 -121 -291 -212 -207 -205 -319 -456 -331 -742 -14 -328 99 -619 329 -844 375 -367 958 -420 1396 -126 334 224 507 568 489 975 -20 467 -335 872 -786 1011 -141 44 -341 60 -486 39z" />
                                <path
                                    d="M2106 4875 c-332 -69 -640 -284 -806 -564 -45 -76 -102 -226 -127 -335 -25 -110 -22 -337 5 -449 51 -205 132 -355 272 -497 74 -76 117 -110 195 -155 454 -264 922 -550 985 -604 160 -136 441 -600 646 -1065 46 -104 76 -138 137 -156 79 -22 156 12 187 83 18 41 18 47 0 227 -11 102 -35 376 -55 610 -30 357 -108 1234 -171 1915 -18 203 -43 286 -122 419 -72 119 -131 193 -234 291 -141 135 -283 215 -475 266 -102 28 -336 35 -437 14z" />
                                <path
                                    d="M5191 4875 c-202 -46 -373 -139 -530 -289 -172 -166 -306 -389 -331 -553 -6 -37 -30 -300 -55 -583 -51 -593 -87 -991 -152 -1700 -55 -596 -55 -599 -27 -636 52 -71 130 -92 201 -55 52 26 67 48 137 201 136 298 368 699 509 883 89 115 108 128 692 479 461 276 482 289 557 355 238 205 373 555 338 873 -30 280 -123 481 -310 671 -118 119 -215 189 -364 260 -160 76 -261 100 -446 105 -112 3 -173 0 -219 -11z" />
                            </g>
                        </svg>
                    </div>
                    <div>
                        <div class="site-title">紫荆论坛</div>
                        <div style="font-size:12px;color:var(--muted)">讨论 · 资讯 · 分享</div>
                    </div>
                </div>
                <div class="search">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
                        <path d="M21 21l-4.35-4.35" stroke="#9aa0b5" stroke-width="2" stroke-linecap="round" />
                        <circle cx="11" cy="11" r="6" stroke="#9aa0b5" stroke-width="2" fill="none" />
                    </svg>
                    <input type="text" placeholder="搜索帖子或用户..." v-model="searchKeyword"
                        @keyup.enter="handleSearch" />
                </div>
                <div class="user-area">
                    <div class="icon-btn" @click="showMessages">
                        <svg viewBox="0 0 24 24" width="16" height="16" stroke="currentColor" fill="none">
                            <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
                        </svg>
                        <span>消息</span>
                        <span v-if="messageCount > 0" class="red-badge">{{ messageCount > 99 ? '99+' : messageCount }}</span>
                    </div>
                    <div class="icon-btn" @click="showActivities">
                        <svg viewBox="0 0 24 24" width="16" height="16" stroke="currentColor" fill="none">
                            <path d="M22 12h-4l-3 9L9 3l-3 9H2" />
                        </svg>
                        <span>动态</span>
                        <span v-if="notificationCount > 0" class="red-badge">{{ notificationCount > 99 ? '99+' : notificationCount }}</span>
                    </div>
                    <div class="avatar-dropdown" @mouseenter="refreshStats">
                        <div class="avatar-btn" @click="goToProfile">
                            <img :src="resolveAvatarUrl(avatarUrl)" alt="头像" @error="handleAvatarError" />
                        </div>
                        <div class="dropdown-content">
                            <div class="dropdown-profile-header">
                                <div class="dropdown-avatar">
                                    <img :src="resolveAvatarUrl(avatarUrl)" alt="头像" @error="handleAvatarError" />
                                </div>
                                <div class="dropdown-user-info">
                                    <div class="dropdown-name">
                                        {{ userName }}
                                        <span class="dropdown-badge">ID：{{ formatUserId(userId) }}</span>
                                    </div>
                                    <div class="dropdown-bio">{{ userBio }}</div>
                                </div>
                            </div>
                            <div class="dropdown-stats">
                                <div class="stat-cell" @click="viewFollowing">
                                    <span class="stat-number">{{ followingCount }}</span>
                                    <span class="stat-label1">关注</span>
                                </div>
                                <div class="stat-cell" @click="viewFollowers">
                                    <span class="stat-number">{{ followerCount }}</span>
                                    <span class="stat-label1">粉丝</span>
                                </div>
                                <div class="stat-cell" @click="viewMyPosts">
                                    <span class="stat-number">{{ myPostCount }}</span>
                                    <span class="stat-label1">帖子</span>
                                </div>
                            </div>
                            <div class="dropdown-menu">
                                <div class="dropdown-item" @click="goToProfile">
                                    <svg viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                                        <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
                                        <circle cx="12" cy="7" r="4" />
                                    </svg>
                                    个人主页
                                </div>
                                <div class="dropdown-item" @click="editProfile">
                                    <svg viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                                        <path d="M20 14.66V20a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h5.34" />
                                        <polygon points="18 2 22 6 12 16 8 16 8 12 18 2" />
                                    </svg>
                                    个人信息
                                </div>
                                <div class="dropdown-item" v-if="userRole >= 2" @click="goToAdmin">
                                    <svg viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                                        <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
                                    </svg>
                                    管理论坛
                                </div>
                                <div class="dropdown-item logout" @click="logout">
                                    <svg viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                                        <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
                                        <polyline points="16 17 21 12 16 7" />
                                        <line x1="21" y1="12" x2="9" y2="12" />
                                    </svg>
                                    退出登录
                                </div>
                            </div>
                        </div>
                    </div>
                    <button v-if="userRole >= 2" class="compose-fab" @click="goToAdmin">管理后台</button>
                    <button class="compose-fab" @click="openCompose">＋ 发布新帖</button>
                </div>
            </div>
        </div>
    `,
    data() {
        return {
            basePath: '',
            userName: '',
            userId: null,
            userBio: '',
            avatarUrl: '',
            defaultAvatar: '/img/头像示例.jpg',

            followingCount: 0,
            followerCount: 0,
            myPostCount: 0,

            messageCount: 0,
            notificationCount: 0,
            userRole: 0,

            searchKeyword: ''
        }
    },
    mounted() {
        this.calculateBasePath();
        this.loadUserInfo();
        this.loadCurrentRole();
        window.addEventListener('message-unread-changed', this.fetchUnreadCount);
    },
    beforeDestroy() {
        window.removeEventListener('message-unread-changed', this.fetchUnreadCount);
    },
    methods: {
        refreshStats() {
            if (!this.userId || !localStorage.getItem('token')) return;
            axios.get(`/api/profiles/${this.userId}/stats`).then(response => {
                const stats = response.data.data || {};
                this.followingCount = Number(stats.followingCount) || 0;
                this.followerCount = Number(stats.followerCount) || 0;
                this.myPostCount = Number(stats.postCount) || 0;
            }).catch(() => {});
        },
        calculateBasePath() {
            const path = window.location.pathname;

            if (path.includes('/user/') || path.includes('/post/') || path.includes('/profile/') || path.includes('/admin/')) {
                this.basePath = '../';
            } else if (path === '/' || path.endsWith('index.html') || path.endsWith('/')) {
                this.basePath = './';
            } else {
                const depth = (path.match(/\//g) || []).length;
                this.basePath = depth > 1 ? '../'.repeat(depth - 1) : './';
            }
        },
        loadUserInfo() {
            this.defaultAvatar = '/img/头像示例.jpg';
            this.avatarUrl = this.avatarUrl || this.defaultAvatar;

            const currentUserId = localStorage.getItem('userId') || localStorage.getItem('currentUserId');

            if (currentUserId && currentUserId !== 'null') {
                this.userId = currentUserId;
            }

            const scopedUserInfo = this.userId ? this.readStoredJson(`userInfo_${this.userId}`) : null;
            const cachedUserInfo = this.readStoredJson('userInfo');
            const userInfo = scopedUserInfo || (
                cachedUserInfo && String(cachedUserInfo.id || cachedUserInfo.userId || '') === String(this.userId || '')
                    ? cachedUserInfo
                    : null
            );
            if (userInfo) {
                const user = userInfo;
                this.userName = user.username || user.userName || this.userName;
                this.userId = user.id || user.userId || this.userId;
                this.userBio = user.signature || user.userBio || this.userBio;
                this.avatarUrl = user.avatar || user.avatarUrl || this.avatarUrl;
                this.followerCount = user.followerCount || this.followerCount;
                this.followingCount = user.followingCount || this.followingCount;
                this.myPostCount = user.postCount || user.myPostCount || this.myPostCount;
            }

            if (this.userId && this.userId !== 'null') {
                axios.get(`/api/user/info/${this.userId}`)
                    .then(response => {
                        if (response.data.code !== 1) {
                            return;
                        }
                        const user = response.data.data;
                        this.userName = user.username || this.userName;
                        this.userId = user.id || this.userId;
                        this.userBio = user.signature || this.userBio;
                        this.avatarUrl = user.avatar || this.defaultAvatar;
                        this.followerCount = user.followerCount || 0;
                        this.followingCount = user.followingCount || 0;
                        this.myPostCount = user.postCount || 0;
                        localStorage.setItem('userInfo', JSON.stringify({ ...user, role: this.userRole }));
                        localStorage.setItem(`userInfo_${user.id}`, JSON.stringify(user));
                        localStorage.setItem('userId', user.id);
                        localStorage.setItem('currentUserId', user.id);
                        axios.get(`/api/profiles/${this.userId}/stats`).then(statsResponse => {
                            const stats = statsResponse.data.data || {};
                            this.followerCount = stats.followerCount || 0;
                            this.followingCount = stats.followingCount || 0;
                            this.myPostCount = stats.postCount || 0;
                        }).catch(() => {});
                        this.fetchUnreadCount();
                    })
                    .catch(error => {
                        console.error('顶部栏获取用户信息失败:', error);
                        this.fetchUnreadCount();
                    });
            } else {
                this.fetchUnreadCount();
            }
            // // 从后端获取最新的用户信息
            // if (this.userId && this.userId !== 'null') {
            //     axios.get(`/api/user/info/${this.userId}`)
            //         .then(response => {
            //             if (response.data.code !== 1) {
            //                 return;
            //             }
            //             const user = response.data.data;
            //             this.userName = user.username || this.userName;
            //             this.userId = user.id || this.userId;
            //             this.userBio = user.signature || this.userBio;
            //             this.avatarUrl = user.avatar || this.defaultAvatar;
            //             this.followerCount = user.followerCount || 0;
            //             this.followingCount = user.followingCount || 0;
            //             this.myPostCount = user.postCount || 0;

            //             // 同时更新隔离存储和兼容存储
            //             localStorage.setItem(`userInfo_${user.id}`, JSON.stringify(user));
            //             localStorage.setItem('userInfo', JSON.stringify(user));
            //             localStorage.setItem('currentUserId', user.id);
            //             localStorage.setItem('userId', user.id);
            //         })
            //         .catch(error => {
            //             console.error('顶部栏获取用户信息失败:', error);
            //         });
            // }
        },

        loadCurrentRole() {
            if (!localStorage.getItem('token')) {
                this.userRole = 0;
                return;
            }
            axios.get('/api/user/current').then(response => {
                this.userRole = Number(response.data.data?.role) || 0;
                localStorage.setItem('role', String(this.userRole));
            }).catch(() => { this.userRole = 0; });
        },

        formatUserId(id) {
            if (!id) return '000000';
            const str = String(id);
            return str.padStart(6, '0');
        },

        readStoredJson(key) {
            const raw = localStorage.getItem(key);
            if (!raw) {
                return null;
            }
            try {
                return JSON.parse(raw);
            } catch (error) {
                console.warn(`顶部栏忽略无效缓存: ${key}`, error);
                localStorage.removeItem(key);
                return null;
            }
        },

        resolveAvatarUrl(avatar) {
            if (!avatar) {
                return this.defaultAvatar || '/img/头像示例.jpg';
            }
            if (avatar.startsWith('http://') || avatar.startsWith('https://') || avatar.startsWith('data:') || avatar.startsWith('blob:')) {
                return avatar;
            }
            if (avatar.startsWith('/')) {
                return avatar;
            }
            return avatar;
        },

        handleAvatarError(event) {
            const fallback = this.defaultAvatar || '/img/头像示例.jpg';
            if (event.target.src.includes('/img/头像示例.jpg')) {
                return;
            }
            event.target.src = fallback;
        },


        returnIndex() {
            window.location.href = this.basePath + 'index.html';
        },

        showMessages() {
            window.location.href = this.basePath + 'user/message.html';
        },

        showActivities() {
            window.location.href = this.basePath + 'user/feed.html';
        },

        viewFollowers() {
            window.location.href = this.basePath + `user/followers.html?userId=${this.userId}`;
        },

        viewFollowing() {
            window.location.href = this.basePath + `user/following.html?userId=${this.userId}`;
        },

        editProfile() {
            const userId = this.userId || localStorage.getItem('userId');
            // window.location.href = this.basePath + 'post/create.html'; // 旧代码跳到了发帖页，保留注释
            // window.location.href = this.basePath + `user/edit-profile.html?userId=${userId}`; // 旧代码直接跳修改页，保留注释，不删除
            window.location.href = this.basePath + `user/info.html?userId=${userId}`;
        },

        logout() {
            localStorage.removeItem('token');
            localStorage.removeItem('userInfo');
            localStorage.removeItem('userId');
            localStorage.removeItem('currentUserId');
            window.location.href = this.basePath + 'login.html';

        },

        openCompose() {
            window.location.href = this.basePath + 'post/create.html';
        },

        goToProfile() {
            const userId = this.userId || localStorage.getItem('userId');
            if (!userId || userId === 'null') {
                window.location.href = this.basePath + 'login.html';
                return;
            }
            // window.location.href = this.basePath + `user/profile.html?userId=${this.userId}`; // old code could produce userId=null
            window.location.href = this.basePath + `user/profile.html?userId=${userId}`;
        },

        viewMyPosts() {
            window.location.href = this.basePath + `user/profile.html?userId=${this.userId}`;
        },

        handleSearch() {
            const keyword = this.searchKeyword?.trim();
            if (!keyword) return;
            const searchUrl = this.basePath + `post/search.html?q=${encodeURIComponent(keyword)}`;
            if (window.location.pathname.includes('search.html')) {
                window.location.href = searchUrl;
            } else {
                window.open(searchUrl, '_blank');
            }
        },

        fetchUnreadCount() {
            if (!localStorage.getItem('token')) {
                this.messageCount = 0;
                return;
            }
            axios.get('/api/messages/unread-count')
                .then(response => {
                    if (response.data.code === 1) this.messageCount = Number(response.data.data?.count) || 0;
                })
                .catch(() => {});
        },

        goToAdmin() {
            window.location.href = this.basePath + 'admin/admin.html';
        }
    }
});
