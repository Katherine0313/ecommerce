// 倒计时 mixin：组件内调用 this.fmtCountdown(target) / this.remainMs(target) / this.isEnded(target)
// target 支持时间戳（数字）或 "yyyy-MM-dd HH:mm:ss" 字符串。
// mixin 每秒刷新 this._now，驱动所有依赖它的计算属性重新求值。
export default {
	data() {
		return {
			_now: Date.now(),
			_timer: null
		}
	},
	mounted() {
		this._timer = setInterval(() => {
			this._now = Date.now()
		}, 1000)
	},
	beforeDestroy() {
		if (this._timer) {
			clearInterval(this._timer)
			this._timer = null
		}
	},
	methods: {
		// 兼容 iOS 微信：把 "yyyy-MM-dd HH:mm:ss" 的横杠换成斜杠再解析
		_parseTime(t) {
			if (t === null || t === undefined || t === '') return 0
			if (typeof t === 'number') return t
			return new Date(String(t).replace(/-/g, '/')).getTime()
		},
		remainMs(target) {
			return Math.max(0, this._parseTime(target) - this._now)
		},
		fmtCountdown(target) {
			const ms = this.remainMs(target)
			if (ms <= 0) return '00:00:00'
			const totalSec = Math.floor(ms / 1000)
			const h = Math.floor(totalSec / 3600)
			const m = Math.floor((totalSec % 3600) / 60)
			const s = totalSec % 60
			const pad = (n) => (n < 10 ? '0' + n : '' + n)
			if (h > 99) {
				return Math.floor(h / 24) + '天' + pad(h % 24) + '时'
			}
			return pad(h) + ':' + pad(m) + ':' + pad(s)
		},
		isEnded(target) {
			return this.remainMs(target) <= 0
		}
	}
}
