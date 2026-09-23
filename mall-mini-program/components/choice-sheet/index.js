Component({
  properties: {
    title: { type: String, value: '请选择' },
    label: { type: String, value: '' },
    value: { type: String, value: '' },
    placeholder: { type: String, value: '请选择' },
    options: { type: Array, value: [] },
    selectedIndex: { type: Number, value: -1 },
    disabled: { type: Boolean, value: false },
    variant: { type: String, value: 'input' }
  },
  data: { visible: false, listHeight: 0 },
  pageLifetimes: { hide() { this.close() } },
  methods: {
    open() {
      if (this.data.disabled || !this.data.options.length || this.data.visible) return
      this.setData({ visible: true, listHeight: Math.min(this.data.options.length * 96, 640) })
    },
    close() { if (this.data.visible) this.setData({ visible: false }) },
    choose(event) {
      if (this.data.disabled || !this.data.visible) return
      const index = Number(event.currentTarget.dataset.index)
      if (!Number.isInteger(index) || index < 0 || index >= this.data.options.length) return
      this.setData({ visible: false })
      this.triggerEvent('change', { value: String(index) })
    },
    stopTap() {},
    stopTouch() {}
  }
})
