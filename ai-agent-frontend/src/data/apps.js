export const apps = [
  {
    id: 'love',
    name: 'AI 恋爱大师',
    english: 'LOVE ADVISOR',
    tagline: '懂你的心事，也懂你的心动。',
    description: '从心动的第一句话，到关系里的小困惑。你的专属情感顾问，陪你认真对待每一份喜欢。',
    icon: 'heart',
    tags: ['情感咨询', '沟通建议', '关系成长'],
    welcome: '今天，有什么想和我聊聊的？',
    intro: '关于心动、相处，或是那些说不出口的心事，我都愿意听。',
    placeholder: '说说你的心事吧，我在听…',
    suggestions: [
      { icon: 'message', title: '不知道怎么开口？', text: '怎么和喜欢的人自然地开启聊天？' },
      { icon: 'heart', title: '读懂彼此的心意', text: '怎么判断对方是否也对我有好感？' },
      { icon: 'sprout', title: '让关系更进一步', text: '我们最近经常因为小事争吵，该怎么改善沟通？' },
      { icon: 'coffee', title: '准备一次美好约会', text: '第一次约会怎么安排，才能自然又不尴尬？' },
    ],
  },
  {
    id: 'manus',
    name: 'AI 超级智能体',
    english: 'SUPER AGENT',
    tagline: '不止于回答，更为你行动。',
    description: '把复杂的任务交给 AI。从信息搜集到分析规划，让想法落地，让每一步都更高效。',
    icon: 'sparkles',
    tags: ['自主规划', '工具调用', '任务执行'],
    welcome: '把目标告诉我，剩下的交给我们。',
    intro: '从一个灵感到一份完整方案，和我一起把复杂的事情变简单。',
    placeholder: '描述你的任务，或告诉我你想实现的目标…',
    suggestions: [
      { icon: 'search', title: '帮我研究一个话题', text: '帮我调研 AI 智能体的应用场景，并整理成一份简洁的报告。' },
      { icon: 'compass', title: '规划下一次出发', text: '帮我制定一份杭州三日旅行计划，兼顾自然风景和当地美食。' },
      { icon: 'file', title: '从灵感到完整方案', text: '帮我规划一个个人知识库产品，梳理核心功能和开发步骤。' },
      { icon: 'lightbulb', title: '拆解一个复杂任务', text: '我想在一个月内入门 Vue 3，请帮我制定每周学习和实践计划。' },
    ],
  },
]

export const getApp = (id) => apps.find((app) => app.id === id)
